package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.*;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.repository.CalendarEventRepository;
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

/** "Today" is 2026-09-20, so ten days of September are left. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfig.class)
class ForecastTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired BankAccountRepository bankAccountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired CalendarEventRepository eventRepository;

    User user;
    BankAccount account;

    @BeforeEach
    void seed() {
        user = newUser();
        account = newAccount(user);
    }

    @Test
    void addsBillsStillToComeAndEverydaySpendingAtTheUsualPace() throws Exception {
        // June to August: 920 of everyday spending over 92 days is 10 a day. Rent and Netflix are
        // on the calendar, so their charges don't count towards the everyday pace.
        for (String month : new String[]{"06", "07", "08"}) {
            spend("Hartley Lettings Rent", "950.00", "2026-" + month + "-25");
            spend("NETFLIX.COM", "10.99", "2026-" + month + "-08");
        }
        spend("Tesco", "300.00", "2026-06-01");
        spend("Tesco", "300.00", "2026-07-10");
        spend("Tesco", "320.00", "2026-08-10");
        spend("Salary", "-2000.00", "2026-08-28");       // money in is never spending
        spend("Tesco", "150.00", "2026-09-05");
        spend("NETFLIX.COM", "10.99", "2026-09-08");

        event("Rent", EventType.PAYMENT, "950.00", "2026-06-25", Recurrence.MONTHLY, "2026-09-25");
        event("Netflix", EventType.SUBSCRIPTION, "10.99", "2026-06-08", Recurrence.MONTHLY, "2026-10-08");
        event("Car tax", EventType.PAYMENT, "180.00", "2026-09-28", Recurrence.NONE, "2026-09-28");
        event("Gym", EventType.SUBSCRIPTION, "25.00", "2026-09-20", Recurrence.MONTHLY, "2026-09-20"); // due today
        event("Renew passport", EventType.TASK, null, "2026-09-22", Recurrence.NONE, "2026-09-22");

        mvc.perform(get("/api/insights").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forecast.spentSoFar").value(160.99))
                .andExpect(jsonPath("$.forecast.billsToCome").value(1130.0))
                .andExpect(jsonPath("$.forecast.billCount").value(2))
                .andExpect(jsonPath("$.forecast.everydayPerDay").value(10.0))
                .andExpect(jsonPath("$.forecast.daysLeft").value(10))
                .andExpect(jsonPath("$.forecast.everydayToCome").value(100.0))
                .andExpect(jsonPath("$.forecast.total").value(1390.99))
                .andExpect(jsonPath("$.forecast.monthEnd").value("2026-09-30"));
    }

    @Test
    void aNewUserGetsAForecastFromThisMonthsOwnPaceOnceThereIsAWeekOfIt() throws Exception {
        spend("Pret", "70.00", "2026-09-10");   // 11 days of history: 6.36 a day

        mvc.perform(get("/api/insights").header("Authorization", auth()))
                .andExpect(jsonPath("$.forecast.everydayPerDay").value(6.36))
                .andExpect(jsonPath("$.forecast.total").value(133.6));
    }

    @Test
    void noForecastFromLessThanAWeekOfHistory() throws Exception {
        spend("Pret", "70.00", "2026-09-16");

        mvc.perform(get("/api/insights").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forecast").value(nullValue()));
    }

    private void spend(String name, String amount, String date) {
        Transaction tx = new Transaction();
        tx.setBankAccount(account);
        tx.setPlaidTransactionId(UUID.randomUUID().toString());
        tx.setName(name);
        tx.setAmount(new BigDecimal(amount));
        tx.setTransactionDate(LocalDate.parse(date));
        transactionRepository.save(tx);
    }

    private void event(String title, EventType type, String amount, String start, Recurrence recurrence, String nextDue) {
        CalendarEvent event = new CalendarEvent();
        event.setUser(user);
        event.setTitle(title);
        event.setType(type);
        event.setAmount(amount == null ? null : new BigDecimal(amount));
        event.setStartDate(LocalDate.parse(start));
        event.setNextDueDate(LocalDate.parse(nextDue));
        event.setRecurrence(recurrence);
        eventRepository.save(event);
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
        u.setFullName("Forecast Test");
        return userRepository.save(u);
    }

    private String auth() {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(user));
    }
}
