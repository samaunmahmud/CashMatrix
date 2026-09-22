package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.*;
import com.expensetracker.expensetracker.repository.*;
import com.expensetracker.expensetracker.security.JwtService;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.BudgetAlertService;
import com.expensetracker.expensetracker.service.delivery.PushGateway;
import com.expensetracker.expensetracker.service.delivery.ReminderDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Today" is 2026-09-20, so September has 11 days left including today. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfig.class)
class BudgetsTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired BankAccountRepository bankAccountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired BudgetRepository budgetRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired PushSubscriptionRepository pushRepository;
    @Autowired BudgetAlertService alerts;
    @Autowired ReminderDeliveryService delivery;
    @MockBean PushGateway pushGateway;

    User user;
    BankAccount account;

    @BeforeEach
    void seed() {
        user = newUser();
        account = new BankAccount();
        account.setUser(user);
        account.setPlaidAccessToken("t-" + UUID.randomUUID());
        account.setPlaidItemId("i-" + UUID.randomUUID());
        account.setPlaidAccountId("a-" + UUID.randomUUID());
        account.setName("Current");
        account.setCurrency("GBP");
        bankAccountRepository.save(account);

        // September so far
        spend("Tesco", "Groceries", null, "60.00", "2026-09-02");
        spend("Sainsbury's", "Groceries", null, "45.50", "2026-09-12");
        spend("Pret", "Restaurants", "Eating out", "8.40", "2026-09-05");   // user re-filed it
        spend("Dishoom", "Restaurants", null, "42.00", "2026-09-14");
        spend("Salary", "Payroll", null, "-2100.00", "2026-09-01");        // money in, never spending
        // August, which must not count towards September
        spend("Tesco", "Groceries", null, "180.00", "2026-08-20");
        spend("Uber", "Taxi", null, "30.00", "2026-08-11");
        spend("Uber", "Taxi", null, "22.00", "2026-07-01");              // history starts on the 1st: July is complete
        spend("Wagamama", "Restaurants", null, "20.00", "2026-08-08");
    }

    // --- overview ------------------------------------------------------------

    @Test
    void showsHowEachBudgetIsDoingThisMonth() throws Exception {
        budget("Groceries", "200");
        budget("groceries ", "300").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("You already have a budget for groceries"));
        budget("Eating out", "10");

        mvc.perform(get("/api/budgets").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.currentMonth").value(true))
                .andExpect(jsonPath("$.daysLeft").value(11))
                .andExpect(jsonPath("$.totalLimit").value(210.0))
                .andExpect(jsonPath("$.totalSpent").value(113.9))
                // sorted by category name
                .andExpect(jsonPath("$.budgets[0].category").value("Eating out"))
                .andExpect(jsonPath("$.budgets[0].spent").value(8.4))
                .andExpect(jsonPath("$.budgets[0].percentUsed").value(84))
                .andExpect(jsonPath("$.budgets[0].status").value("NEAR_LIMIT"))
                .andExpect(jsonPath("$.budgets[1].category").value("Groceries"))
                .andExpect(jsonPath("$.budgets[1].spent").value(105.5))
                .andExpect(jsonPath("$.budgets[1].remaining").value(94.5))
                .andExpect(jsonPath("$.budgets[1].percentUsed").value(53))
                .andExpect(jsonPath("$.budgets[1].status").value("ON_TRACK"));
    }

    @Test
    void anEarlierMonthCountsThatMonthsSpendingOnly() throws Exception {
        budget("Groceries", "150");

        mvc.perform(get("/api/budgets").param("month", "2026-08").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentMonth").value(false))
                .andExpect(jsonPath("$.daysLeft").value(0))
                .andExpect(jsonPath("$.budgets[0].spent").value(180.0))
                .andExpect(jsonPath("$.budgets[0].remaining").value(-30.0))
                .andExpect(jsonPath("$.budgets[0].status").value("OVER"));

        mvc.perform(get("/api/budgets").param("month", "2026-10").header("Authorization", auth()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/budgets").param("month", "September").header("Authorization", auth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suggestsCategoriesWithoutABudgetAndATypicalMonthlyLimit() throws Exception {
        budget("Groceries", "200");

        // Based on the complete months, July and August. Taxi: 52 / 2 = 26, rounded up to 30.
        // September is still under way, so "Eating out" (only seen this month) isn't suggested.
        mvc.perform(get("/api/budgets").header("Authorization", auth()))
                .andExpect(jsonPath("$.suggestions.length()").value(2))
                .andExpect(jsonPath("$.suggestions[0].category").value("Taxi"))
                .andExpect(jsonPath("$.suggestions[0].suggestedLimit").value(30.0))
                .andExpect(jsonPath("$.suggestions[1].category").value("Restaurants"))
                .andExpect(jsonPath("$.suggestions[1].suggestedLimit").value(10.0))
                .andExpect(jsonPath("$.suggestions[?(@.category=='Groceries')]").isEmpty())
                .andExpect(jsonPath("$.suggestions[?(@.category=='Payroll')]").isEmpty());
    }

    @Test
    void someoneWithOnlyAPartMonthOfHistoryGetsSuggestionsFromThisMonthSoFar() throws Exception {
        user = newUser();
        account = new BankAccount();
        account.setUser(user);
        account.setPlaidAccessToken("t");
        account.setPlaidItemId("i");
        account.setPlaidAccountId("a-" + UUID.randomUUID());
        account.setName("New");
        bankAccountRepository.save(account);
        spend("Tesco", "Groceries", null, "33.00", "2026-09-10");

        mvc.perform(get("/api/budgets").header("Authorization", auth()))
                .andExpect(jsonPath("$.suggestions[0].category").value("Groceries"))
                .andExpect(jsonPath("$.suggestions[0].suggestedLimit").value(35.0));
    }

    @Test
    void editingAndDeletingOnlyWorkOnYourOwnBudgets() throws Exception {
        long id = createdId(budget("Groceries", "200"));

        mvc.perform(put("/api/budgets/" + id).header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"Food shopping\",\"monthlyLimit\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Food shopping"))
                .andExpect(jsonPath("$.spent").value(0));

        String stranger = "Bearer " + jwtService.generateToken(new UserPrincipal(newUser()));
        mvc.perform(put("/api/budgets/" + id).header("Authorization", stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"Mine now\",\"monthlyLimit\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/budgets/" + id).header("Authorization", stranger))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/budgets/" + id).header("Authorization", auth()))
                .andExpect(status().isNoContent());
        assertThat(budgetRepository.findByUserOrderByCategoryAsc(user)).isEmpty();
    }

    @Test
    void rejectsBadInput() throws Exception {
        for (String body : List.of(
                "{\"category\":\"\",\"monthlyLimit\":50}",
                "{\"category\":\"Groceries\",\"monthlyLimit\":0}",
                "{\"category\":\"Groceries\",\"monthlyLimit\":-5}",
                "{\"category\":\"Groceries\",\"monthlyLimit\":10.555}",
                "{\"category\":\"Groceries\"}")) {
            mvc.perform(post("/api/budgets").header("Authorization", auth())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/budgets")).andExpect(status().isUnauthorized());
    }

    // --- alerts --------------------------------------------------------------

    @Test
    void warnsOnceWhenNearlyUsedAndOnceWhenOver() throws Exception {
        budget("Groceries", "130"); // 105.50 spent = 81%: nearly used, and the user is in the app

        List<Notification> first = alertsFor(user);
        assertThat(first).hasSize(1);
        assertThat(first.get(0).getTitle()).isEqualTo("Groceries budget nearly used");
        assertThat(first.get(0).getMessage())
                .isEqualTo("You've spent £105.50 of your £130.00 Groceries budget for September. £24.50 left.");
        assertThat(first.get(0).openPath()).isEqualTo("/budgets");
        assertThat(first.get(0).isPushSent()).as("user was present, so no push").isTrue();

        alerts.checkAll();
        alerts.check(user);
        assertThat(alertsFor(user)).as("never repeated").hasSize(1);

        spend("Lidl", "Groceries", null, "40.00", "2026-09-19");
        alerts.checkAll();

        List<Notification> after = alertsFor(user);
        assertThat(after).hasSize(2);
        Notification over = after.stream().filter(n -> n.getTitle().startsWith("Over")).findFirst().orElseThrow();
        assertThat(over.getMessage())
                .isEqualTo("You've spent £145.50 of your £130.00 Groceries budget for September, £15.50 over.");
        assertThat(over.isPushSent()).as("found by the background job, so still to be pushed").isFalse();
    }

    @Test
    void goingStraightPastTheLimitSkipsTheNearlyUsedWarning() throws Exception {
        budget("Groceries", "100");

        assertThat(alertsFor(user)).extracting(Notification::getTitle).containsExactly("Over your Groceries budget");

        spend("Refund", "Groceries", null, "-20.00", "2026-09-18"); // money back does not reduce spending
        alerts.check(user);
        assertThat(alertsFor(user)).hasSize(1);
    }

    @Test
    void budgetAlertsAppearInTheAlertsListAndArePushedWithALinkToBudgets() throws Exception {
        Budget budget = new Budget();
        budget.setUser(user);
        budget.setCategory("Eating out");
        budget.setMonthlyLimit(new BigDecimal("5"));
        budgetRepository.save(budget);
        when(pushGateway.isConfigured()).thenReturn(true);
        PushSubscription device = new PushSubscription();
        device.setUser(user);
        device.setEndpoint("https://push.example/" + UUID.randomUUID());
        device.setP256dh("k");
        device.setAuth("a");
        pushRepository.save(device);
        when(pushGateway.send(any(), any())).thenReturn(PushGateway.Result.SENT);

        alerts.checkAll();
        delivery.deliverPending();

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(pushGateway, atLeastOnce()).send(argThat(d -> d.getEndpoint().equals(device.getEndpoint())), payload.capture());
        assertThat(payload.getValue()).contains("\"url\":\"/budgets\"").contains("Over your Eating out budget");

        mvc.perform(get("/api/notifications").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Over your Eating out budget"))
                .andExpect(jsonPath("$[0].link").value("/budgets"))
                .andExpect(jsonPath("$[0].eventId").value(nullValue()));
    }

    // --- helpers -------------------------------------------------------------

    private List<Notification> alertsFor(User u) {
        return notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(u).stream()
                .filter(n -> n.getAlertKey() != null)
                .toList();
    }

    private ResultActions budget(String category, String limit) throws Exception {
        return mvc.perform(post("/api/budgets").header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"" + category + "\",\"monthlyLimit\":" + limit + "}"));
    }

    private long createdId(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private void spend(String name, String plaidCategory, String userCategory, String amount, String date) {
        Transaction tx = new Transaction();
        tx.setBankAccount(account);
        tx.setPlaidTransactionId(UUID.randomUUID().toString());
        tx.setName(name);
        tx.setPlaidCategory(plaidCategory);
        tx.setUserCategory(userCategory);
        tx.setAmount(new BigDecimal(amount));
        tx.setTransactionDate(LocalDate.parse(date));
        transactionRepository.save(tx);
    }

    private User newUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@example.com");
        u.setPasswordHash("x");
        u.setFullName("Budget Test");
        return userRepository.save(u);
    }

    private String auth() {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(user));
    }
}
