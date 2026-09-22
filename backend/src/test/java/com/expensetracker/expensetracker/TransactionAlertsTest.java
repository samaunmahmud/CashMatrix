package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.Notification;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.repository.NotificationRepository;
import com.expensetracker.expensetracker.repository.UserRepository;
import com.expensetracker.expensetracker.security.JwtService;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.PlaidService;
import com.expensetracker.expensetracker.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Today" is 2026-09-20. Each sync returns whatever {@link #bank} holds at the time. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfig.class)
class TransactionAlertsTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired BankAccountRepository bankAccountRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired TransactionService transactionService;
    @MockBean PlaidService plaidService;

    User user;
    BankAccount account;
    String token;
    List<Map<String, Object>> bank;

    @BeforeEach
    void linkABank() {
        user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("x");
        user.setFullName("Alerts Test");
        userRepository.save(user);

        token = "access-" + UUID.randomUUID();
        account = new BankAccount();
        account.setUser(user);
        account.setPlaidAccessToken(token);
        account.setPlaidItemId("item");
        account.setPlaidAccountId("acc-" + UUID.randomUUID());
        account.setName("Club Current Account");
        account.setMask("4821");
        account.setCurrency("GBP");
        bankAccountRepository.save(account);

        bank = new ArrayList<>();
        when(plaidService.getTransactions(eq(token), anyString(), anyString(), anyInt()))
                .thenAnswer(call -> {
                    int offset = call.getArgument(3);
                    List<Map<String, Object>> page = offset == 0 ? List.copyOf(bank) : List.of();
                    return Map.of("total_transactions", bank.size(), "transactions", page);
                });
        when(plaidService.getAccounts(token)).thenReturn(Map.of("accounts", List.of()));
    }

    @Test
    void theFirstImportIsQuietThenNewTransactionsRaiseAlerts() {
        bank.add(tx("Tesco Superstore", 64.20, "2026-09-19", false));
        bank.add(tx("Salary ACME LTD", -2450.00, "2026-09-18", false));
        transactionService.syncTransactions(user);
        assertThat(alerts()).as("months of history arrive at once on the first sync").isEmpty();

        bank.add(tx("NETFLIX.COM 866-579", 10.99, "2026-09-20", false));  // under the £25 default
        bank.add(tx("Dishoom Kings Cross", 46.50, "2026-09-20", false));
        bank.add(tx("HMRC Refund", -120.00, "2026-09-19", false));
        bank.add(tx("Pret", 30.00, "2026-09-20", true));                  // pending: waits until it settles
        bank.add(tx("Old Shop", 99.00, "2026-09-10", false));             // turned up late: not news
        transactionService.syncTransactions(user, false);

        List<Notification> alerts = alerts();
        assertThat(alerts).extracting(Notification::getTitle)
                .containsExactlyInAnyOrder("£46.50 spent at Dishoom Kings Cross", "£120.00 received from HMRC Refund");
        Notification dishoom = alerts.stream().filter(n -> n.getTitle().contains("Dishoom")).findFirst().orElseThrow();
        assertThat(dishoom.getMessage()).isEqualTo("Paid from Club Current Account ending 4821 on Sun 20 Sept.");
        assertThat(dishoom.openPath()).isEqualTo("/accounts/" + account.getId());
        assertThat(dishoom.isPushSent()).as("background sync: still to be pushed").isFalse();

        transactionService.syncTransactions(user, false);
        assertThat(alerts()).as("never repeated").hasSize(2);
    }

    @Test
    void usersChooseTheMinimumOrSwitchAlertsOff() throws Exception {
        transactionService.syncTransactions(user); // first import, nothing in it

        settings("{\"transactionAlertMinimum\": 5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionAlertsEnabled").value(true))
                .andExpect(jsonPath("$.transactionAlertMinimum").value(5));
        bank.add(tx("NETFLIX.COM", 10.99, "2026-09-20", false));
        transactionService.syncTransactions(user);
        assertThat(alerts()).extracting(Notification::getTitle).containsExactly("£10.99 spent at Netflix");
        assertThat(alerts().get(0).isPushSent()).as("user synced it themselves").isTrue();

        settings("{\"transactionAlertsEnabled\": false}").andExpect(jsonPath("$.transactionAlertsEnabled").value(false));
        bank.add(tx("Currys", 229.00, "2026-09-20", false));
        transactionService.syncTransactions(user);
        assertThat(alerts()).hasSize(1);

        settings("{\"transactionAlertMinimum\": -1}").andExpect(status().isBadRequest());
        mvc.perform(get("/api/settings/notifications").header("Authorization", auth()))
                .andExpect(jsonPath("$.transactionAlertMinimum").value(5.0))
                .andExpect(jsonPath("$.emailEnabled").value(false));
    }

    @Test
    void transactionsCarryTheirAccountAndATidyMerchantName() throws Exception {
        bank.add(tx("NETFLIX.COM 866-579-7172", 10.99, "2026-09-03", false));
        transactionService.syncTransactions(user);

        mvc.perform(get("/api/transactions").header("Authorization", auth()))
                .andExpect(jsonPath("$[0].accountId").value(account.getId()))
                .andExpect(jsonPath("$[0].merchant").value("Netflix"))
                .andExpect(jsonPath("$[0].name").value("NETFLIX.COM 866-579-7172"))
                .andExpect(jsonPath("$[0].bankAccount").doesNotExist());
    }

    // --- helpers -------------------------------------------------------------

    private List<Notification> alerts() {
        return notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(user);
    }

    private org.springframework.test.web.servlet.ResultActions settings(String json) throws Exception {
        return mvc.perform(put("/api/settings/notifications").header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Map<String, Object> tx(String name, double amount, String date, boolean pending) {
        return Map.of(
                "transaction_id", UUID.randomUUID().toString(),
                "account_id", account.getPlaidAccountId(),
                "name", name,
                "amount", BigDecimal.valueOf(amount),
                "date", date,
                "pending", pending,
                "category", List.of("Shops"));
    }

    private String auth() {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(user));
    }
}
