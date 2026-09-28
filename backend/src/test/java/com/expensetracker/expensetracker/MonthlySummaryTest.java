package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.*;
import com.expensetracker.expensetracker.repository.*;
import com.expensetracker.expensetracker.security.JwtService;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.MonthlySummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Today" is 2026-10-02, so last month is September. */
@SpringBootTest
@AutoConfigureMockMvc
class MonthlySummaryTest {

    @TestConfiguration
    static class EarlyOctober {
        @Bean
        @Primary
        Clock earlyOctober() {
            return Clock.fixed(Instant.parse("2026-10-02T08:00:00Z"), ZoneId.of("Europe/London"));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired BankAccountRepository bankAccountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired BudgetRepository budgetRepository;
    @Autowired SavingsGoalRepository goalRepository;
    @Autowired GoalContributionRepository contributionRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired MonthlySummaryService summaryService;

    User user;
    BankAccount account;

    @BeforeEach
    void seed() {
        user = newUser();
        account = account(user);
        // August (history starts on the 1st, so it compares fairly)
        spend("Tesco", "Groceries", "150.00", "2026-08-01");
        spend("Octopus Energy", "Utilities", "50.00", "2026-08-15");
        // September
        spend("Tesco Express", "Groceries", "90.00", "2026-09-03");
        spend("Sainsbury's", "Groceries", "60.00", "2026-09-10");
        spend("DISHOOM LONDON", "Restaurants", "70.00", "2026-09-12");
        spend("Octopus Energy", "Utilities", "50.00", "2026-09-15");
        spend("Salary", "Payroll", "-2000.00", "2026-09-25");
        // October, which must not count
        spend("Tesco", "Groceries", "500.00", "2026-10-01");

        budget("Groceries", "120");  // 150 spent: over
        budget("Utilities", "60");   // 50 spent: nearly used, but kept

        SavingsGoal goal = new SavingsGoal();
        goal.setUser(user);
        goal.setName("Holiday");
        goal.setTargetAmount(new BigDecimal("1000"));
        goal.setSavedAmount(new BigDecimal("80"));
        goalRepository.save(goal);
        contribution(goal, "100", "2026-09-05");
        contribution(goal, "-20", "2026-09-20");
        contribution(goal, "999", "2026-08-05"); // another month
    }

    @Test
    void summarisesLastMonthByDefault() throws Exception {
        mvc.perform(get("/api/insights/summary").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.transactionCount").value(5))
                .andExpect(jsonPath("$.spent").value(270.0))
                .andExpect(jsonPath("$.moneyIn").value(2000.0))
                .andExpect(jsonPath("$.net").value(1730.0))
                .andExpect(jsonPath("$.dailyAverage").value(9.0))
                .andExpect(jsonPath("$.previousSpent").value(200.0))
                .andExpect(jsonPath("$.changePercent").value(35))
                .andExpect(jsonPath("$.topCategories[0].category").value("Groceries"))
                .andExpect(jsonPath("$.topCategories[0].amount").value(150.0))
                .andExpect(jsonPath("$.topCategories[0].share").value(56))
                .andExpect(jsonPath("$.biggestPurchases[0].merchant").value("Tesco Express"))
                .andExpect(jsonPath("$.biggestPurchases[1].merchant").value("Dishoom London"))
                .andExpect(jsonPath("$.biggestPurchases.length()").value(3))
                .andExpect(jsonPath("$.budgets[0].category").value("Groceries"))
                .andExpect(jsonPath("$.budgets[0].status").value("OVER"))
                .andExpect(jsonPath("$.budgets[1].status").value("NEAR_LIMIT"))
                .andExpect(jsonPath("$.savedToGoals").value(80.0));
    }

    @Test
    void theMonthUnderWayAveragesOverTheDaysSoFarAndTheFutureIsRefused() throws Exception {
        mvc.perform(get("/api/insights/summary").param("month", "2026-10").header("Authorization", auth()))
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.dailyAverage").value(250.0));
        mvc.perform(get("/api/insights/summary").param("month", "2026-11").header("Authorization", auth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noComparisonWhenTheHistoryDoesNotCoverTheMonthBefore() throws Exception {
        mvc.perform(get("/api/insights/summary").param("month", "2026-08").header("Authorization", auth()))
                .andExpect(jsonPath("$.spent").value(200.0))
                .andExpect(jsonPath("$.previousSpent").doesNotExist())
                .andExpect(jsonPath("$.changePercent").doesNotExist());
    }

    @Test
    void sendsLastMonthsSummaryOnceAsAnAlert() {
        summaryService.sendMonthlySummaries();
        summaryService.sendMonthlySummaries();

        List<Notification> alerts = notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(user);
        assertThat(alerts).singleElement().satisfies(n -> {
            assertThat(n.getTitle()).isEqualTo("Your September summary");
            assertThat(n.getLink()).isEqualTo("/summary?month=2026-09");
            assertThat(n.getMessage()).isEqualTo("""
                    You spent £270.00 in September, 35% more than in August.
                    Money in: £2,000.00.
                    Most went on Groceries (£150.00), Restaurants (£70.00), Utilities (£50.00).
                    Budgets: 1 of 2 kept.
                    You saved £80.00 towards your goals.""");
            assertThat(n.isEmailSent()).as("goes out by email when that is on").isFalse();
        });
    }

    @Test
    void noSummaryWhenSwitchedOffOrThereWasNothingThatMonth() throws Exception {
        mvc.perform(put("/api/settings/notifications").header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monthlySummaryEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlySummaryEnabled").value(false));

        User quiet = newUser();
        account(quiet); // linked a bank but nothing in September

        summaryService.sendMonthlySummaries();

        assertThat(notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(user)).isEmpty();
        assertThat(notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(quiet)).isEmpty();
    }

    private void spend(String name, String category, String amount, String date) {
        Transaction tx = new Transaction();
        tx.setBankAccount(account);
        tx.setPlaidTransactionId(UUID.randomUUID().toString());
        tx.setName(name);
        tx.setPlaidCategory(category);
        tx.setAmount(new BigDecimal(amount));
        tx.setTransactionDate(LocalDate.parse(date));
        transactionRepository.save(tx);
    }

    private void budget(String category, String limit) {
        Budget budget = new Budget();
        budget.setUser(user);
        budget.setCategory(category);
        budget.setMonthlyLimit(new BigDecimal(limit));
        budgetRepository.save(budget);
    }

    private void contribution(SavingsGoal goal, String amount, String date) {
        GoalContribution c = new GoalContribution();
        c.setGoal(goal);
        c.setAmount(new BigDecimal(amount));
        c.setMadeOn(LocalDate.parse(date));
        contributionRepository.save(c);
    }

    private BankAccount account(User owner) {
        BankAccount a = new BankAccount();
        a.setUser(owner);
        a.setPlaidAccessToken("t-" + UUID.randomUUID());
        a.setPlaidItemId("i-" + UUID.randomUUID());
        a.setPlaidAccountId("a-" + UUID.randomUUID());
        a.setName("Current");
        a.setCurrency("GBP");
        return bankAccountRepository.save(a);
    }

    private User newUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@example.com");
        u.setPasswordHash("x");
        u.setFullName("Summary Test");
        return userRepository.save(u);
    }

    private String auth() {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(user));
    }
}
