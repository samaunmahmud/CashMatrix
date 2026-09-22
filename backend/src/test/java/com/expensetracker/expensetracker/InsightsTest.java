package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.Transaction;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.repository.TransactionRepository;
import com.expensetracker.expensetracker.repository.UserRepository;
import com.expensetracker.expensetracker.security.JwtService;
import com.expensetracker.expensetracker.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Today" is 2026-09-20. The synced history starts on 24 June, part-way through the month. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfig.class)
class InsightsTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired BankAccountRepository bankAccountRepository;
    @Autowired TransactionRepository transactionRepository;

    User user;
    BankAccount account;

    @BeforeEach
    void seed() {
        user = newUser();
        account = newAccount(user);

        spend("Tesco", "Groceries", "20.00", "2026-06-24");
        spend("Tesco", "Groceries", "100.00", "2026-07-03");
        spend("Salary", "Payroll", "-2000.00", "2026-07-28");
        spend("Tesco", "Groceries", "80.00", "2026-08-10");
        spend("Uber", "Taxi", "15.00", "2026-08-19");
        spend("Uber", "Taxi", "25.00", "2026-08-25");   // after the 20th: not in "same time last month"
        spend("Tesco", "Groceries", "60.00", "2026-09-04");
        spend("Pret", "Restaurants", "12.50", "2026-09-18");
    }

    @Test
    void totalsEachMonthSinceTheHistoryStarts() throws Exception {
        mvc.perform(get("/api/insights").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyStart").value("2026-06-24"))
                // Six months asked for, but April and May are before the history, so they're left out.
                .andExpect(jsonPath("$.months.length()").value(4))
                .andExpect(jsonPath("$.months[0].month").value("2026-06"))
                .andExpect(jsonPath("$.months[0].partial").value(true))
                .andExpect(jsonPath("$.months[1].month").value("2026-07"))
                .andExpect(jsonPath("$.months[1].spent").value(100.0))
                .andExpect(jsonPath("$.months[1].moneyIn").value(2000.0))
                .andExpect(jsonPath("$.months[1].partial").value(false))
                .andExpect(jsonPath("$.months[2].spent").value(120.0))
                .andExpect(jsonPath("$.months[3].month").value("2026-09"))
                .andExpect(jsonPath("$.months[3].spent").value(72.5))
                .andExpect(jsonPath("$.months[3].partial").value(true));
    }

    @Test
    void comparesThisMonthWithTheSamePointLastMonth() throws Exception {
        mvc.perform(get("/api/insights").param("months", "3").header("Authorization", auth()))
                .andExpect(jsonPath("$.months.length()").value(3))
                .andExpect(jsonPath("$.spentSoFar").value(72.5))
                .andExpect(jsonPath("$.spentSameTimeLastMonth").value(95.0))
                .andExpect(jsonPath("$.categories[0].category").value("Groceries"))
                .andExpect(jsonPath("$.categories[0].thisMonth").value(60.0))
                .andExpect(jsonPath("$.categories[0].lastMonth").value(80.0))
                .andExpect(jsonPath("$.categories[1].category").value("Restaurants"))
                .andExpect(jsonPath("$.categories[1].lastMonth").value(0))
                .andExpect(jsonPath("$.categories[2].category").value("Taxi"))
                .andExpect(jsonPath("$.categories[2].thisMonth").value(0))
                .andExpect(jsonPath("$.categories[2].lastMonth").value(40.0));
    }

    @Test
    void noComparisonWhenTheHistoryDoesNotCoverLastMonth() throws Exception {
        User newcomer = newUser();
        BankAccount a = newAccount(newcomer);
        account = a;
        spend("Tesco", "Groceries", "30.00", "2026-08-15");
        spend("Tesco", "Groceries", "10.00", "2026-09-02");

        mvc.perform(get("/api/insights").header("Authorization", auth(newcomer)))
                .andExpect(jsonPath("$.spentSoFar").value(10.0))
                .andExpect(jsonPath("$.spentSameTimeLastMonth").value(nullValue()));
    }

    @Test
    void emptyForSomeoneWithNoBankAndRejectsSillyRanges() throws Exception {
        User nobody = newUser();
        mvc.perform(get("/api/insights").header("Authorization", auth(nobody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months.length()").value(0))
                .andExpect(jsonPath("$.historyStart").value(nullValue()));

        mvc.perform(get("/api/insights").param("months", "0").header("Authorization", auth()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/insights").param("months", "13").header("Authorization", auth()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/insights").param("months", "lots").header("Authorization", auth()))
                .andExpect(status().isBadRequest());
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

    private BankAccount newAccount(User owner) {
        BankAccount a = new BankAccount();
        a.setUser(owner);
        a.setPlaidAccessToken("t-" + UUID.randomUUID());
        a.setPlaidItemId("i-" + UUID.randomUUID());
        a.setPlaidAccountId("a-" + UUID.randomUUID());
        a.setName("Current");
        return bankAccountRepository.save(a);
    }

    private User newUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@example.com");
        u.setPasswordHash("x");
        u.setFullName("Insights Test");
        return userRepository.save(u);
    }

    private String auth() {
        return auth(user);
    }

    private String auth(User u) {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(u));
    }
}
