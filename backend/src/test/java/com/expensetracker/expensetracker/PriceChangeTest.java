package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.*;
import com.expensetracker.expensetracker.repository.*;
import com.expensetracker.expensetracker.service.PriceChangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** "Today" is 2026-09-20. */
@SpringBootTest
@Import(FixedClockTestConfig.class)
class PriceChangeTest {

    @Autowired PriceChangeService priceChangeService;
    @Autowired UserRepository userRepository;
    @Autowired BankAccountRepository bankAccountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired CalendarEventRepository eventRepository;
    @Autowired NotificationRepository notificationRepository;

    User user;
    BankAccount account;

    @BeforeEach
    void seed() {
        user = newUser();
        account = newAccount(user);
    }

    @Test
    void aSubscriptionChargedAtANewPriceRaisesOneAlertAndUpdatesTheCalendar() {
        CalendarEvent netflix = event("Netflix", EventType.SUBSCRIPTION, "10.99", Recurrence.MONTHLY);
        List<Transaction> charges = List.of(charge("NETFLIX.COM 866-579", "12.99", "2026-09-08"));

        assertThat(priceChangeService.check(user, charges, false)).isEqualTo(1);

        Notification alert = notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(user).get(0);
        assertThat(alert.getTitle()).isEqualTo("Netflix has gone up to £12.99");
        assertThat(alert.getMessage()).isEqualTo("It was £10.99. Charged £12.99 on Tue 8 Sept. Your calendar now shows the new price.");
        assertThat(alert.getLink()).isEqualTo("/calendar?date=2026-10-08");
        assertThat(alert.isEmailSent()).isFalse();   // raised in the background, so it still goes out by email or push
        assertThat(eventRepository.findById(netflix.getId()).orElseThrow().getAmount()).isEqualByComparingTo("12.99");

        // The next charge at the new price is nothing new.
        assertThat(priceChangeService.check(user, List.of(charge("NETFLIX.COM", "12.99", "2026-09-19")), false)).isZero();
    }

    @Test
    void aBillMatchesByAnyWordOfItsNameAndCanGoDown() {
        event("Rent", EventType.PAYMENT, "950.00", Recurrence.MONTHLY);

        assertThat(priceChangeService.check(user, List.of(charge("HARTLEY LETTINGS RENT", "900.00", "2026-09-01")), true)).isEqualTo(1);

        Notification alert = notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(user).get(0);
        assertThat(alert.getTitle()).isEqualTo("Rent has gone down to £900.00");
        assertThat(alert.isEmailSent()).isTrue();
    }

    @Test
    void smallWobblesAreIgnored() {
        event("Octopus Energy", EventType.PAYMENT, "950.00", Recurrence.MONTHLY);
        event("Spotify", EventType.SUBSCRIPTION, "11.99", Recurrence.MONTHLY);

        assertThat(priceChangeService.check(user, List.of(
                charge("Octopus Energy", "955.00", "2026-09-05"),    // £5, but only half a per cent
                charge("Spotify AB", "12.39", "2026-09-15")), false)) // 40p
                .isZero();
    }

    @Test
    void onlyRegularBillsRecentChargesAndMatchingMerchantsCount() {
        event("Car tax", EventType.PAYMENT, "180.00", Recurrence.NONE);
        event("Book dentist", EventType.TASK, null, Recurrence.MONTHLY);
        event("Netflix", EventType.SUBSCRIPTION, "10.99", Recurrence.MONTHLY);

        assertThat(priceChangeService.check(user, List.of(
                charge("DVLA Car tax", "190.00", "2026-09-10"),       // a one-off
                charge("Tesco", "54.20", "2026-09-10"),               // not a bill
                charge("NETFLIX.COM", "12.99", "2026-07-08"),         // too long ago to be news
                charge("NETFLIX.COM", "-10.99", "2026-09-10")), false)) // a refund
                .isZero();
    }

    private CalendarEvent event(String title, EventType type, String amount, Recurrence recurrence) {
        CalendarEvent event = new CalendarEvent();
        event.setUser(user);
        event.setTitle(title);
        event.setType(type);
        event.setAmount(amount == null ? null : new BigDecimal(amount));
        event.setStartDate(LocalDate.parse("2026-06-08"));
        event.setNextDueDate(LocalDate.parse("2026-10-08"));
        event.setRecurrence(recurrence);
        return eventRepository.save(event);
    }

    private Transaction charge(String name, String amount, String date) {
        Transaction tx = new Transaction();
        tx.setBankAccount(account);
        tx.setPlaidTransactionId(UUID.randomUUID().toString());
        tx.setName(name);
        tx.setAmount(new BigDecimal(amount));
        tx.setTransactionDate(LocalDate.parse(date));
        return transactionRepository.save(tx);
    }

    private BankAccount newAccount(User owner) {
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
        u.setFullName("Price Test");
        return userRepository.save(u);
    }
}
