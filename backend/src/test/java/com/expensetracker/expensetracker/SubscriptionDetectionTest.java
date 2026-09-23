package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.dto.SubscriptionSuggestion;
import com.expensetracker.expensetracker.model.*;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.repository.CalendarEventRepository;
import com.expensetracker.expensetracker.repository.TransactionRepository;
import com.expensetracker.expensetracker.repository.UserRepository;
import com.expensetracker.expensetracker.security.JwtService;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.SubscriptionDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Today" is 2026-09-20. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfig.class)
class SubscriptionDetectionTest {

    @Autowired SubscriptionDetectionService detection;
    @Autowired UserRepository userRepository;
    @Autowired BankAccountRepository bankAccountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired CalendarEventRepository eventRepository;
    @Autowired JwtService jwtService;
    @Autowired MockMvc mvc;

    User user;
    BankAccount account;

    @BeforeEach
    void seedTransactions() {
        user = newUser();
        account = new BankAccount();
        account.setUser(user);
        account.setPlaidAccessToken("t-" + UUID.randomUUID());
        account.setPlaidItemId("i-" + UUID.randomUUID());
        account.setPlaidAccountId("a-" + UUID.randomUUID());
        account.setName("Current");
        bankAccountRepository.save(account);

        // Real subscriptions
        charge("NETFLIX.COM 866-579-7172", "9.99", "2026-07-03", "2026-08-03", "2026-09-03");   // monthly, 3 charges
        charge("Spotify AB", "11.99", "2026-08-15", "2026-09-15");                             // monthly, 2 charges
        charge("PureGym Ltd", "6.00", "2026-08-30", "2026-09-06", "2026-09-13");               // weekly, 3 charges

        // Things that must NOT be flagged
        charge("Tesco Superstore", "54.20", "2026-08-01", "2026-08-09", "2026-08-25", "2026-09-02", "2026-09-14"); // irregular
        charge("Tesco Superstore", "12.10", "2026-08-05");
        charge("Adobe", "19.99", "2026-09-01");                                                // a single charge
        charge("Old Magazine", "4.50", "2026-05-05", "2026-06-05");                            // stopped months ago
        charge("Coffee Club", "3.00", "2026-09-06", "2026-09-13");                             // weekly but only twice
        charge("Utility Co", "50.00", "2026-08-01");
        charge("Utility Co", "65.00", "2026-09-01");                                           // amount varies by 30%
        charge("Cinema", "9.00", "2026-09-10", "2026-09-10", "2026-09-10");                    // same day, not a rhythm
        charge("Netflix refund", "-9.99", "2026-09-05");                                       // money in
    }

    @Test
    void findsRealSubscriptionsAndIgnoresEverythingElse() {
        List<SubscriptionSuggestion> found = detection.detect(user);

        assertThat(found).extracting(SubscriptionSuggestion::key)
                .containsExactlyInAnyOrder("netflix", "spotify", "puregym");
    }

    @Test
    void describesEachSuggestionAccurately() {
        SubscriptionSuggestion netflix = find("netflix");
        assertThat(netflix.name()).isEqualTo("Netflix");
        assertThat(netflix.amount()).isEqualByComparingTo("9.99");
        assertThat(netflix.recurrence()).isEqualTo(Recurrence.MONTHLY);
        assertThat(netflix.lastCharged()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(netflix.nextExpected()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(netflix.occurrences()).isEqualTo(3);
        assertThat(netflix.confidence()).isEqualTo("HIGH");

        SubscriptionSuggestion spotify = find("spotify");
        assertThat(spotify.name()).as("keeps the merchant's own capitals").isEqualTo("Spotify AB");
        assertThat(spotify.confidence()).as("only two charges to go on").isEqualTo("MEDIUM");
        assertThat(spotify.nextExpected()).isEqualTo(LocalDate.of(2026, 10, 15));

        SubscriptionSuggestion gym = find("puregym");
        assertThat(gym.name()).as("drops 'Ltd', keeps CamelCase").isEqualTo("PureGym");
        assertThat(gym.recurrence()).isEqualTo(Recurrence.WEEKLY);
        assertThat(gym.nextExpected()).as("13 Sep + 7 days = today").isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    void acceptingAddsAMonthlySubscriptionToTheCalendarAndStopsSuggestingIt() throws Exception {
        mvc.perform(post("/api/subscriptions/suggestions/netflix/accept").header("Authorization", "Bearer " + token(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Netflix"))
                .andExpect(jsonPath("$.type").value("SUBSCRIPTION"))
                .andExpect(jsonPath("$.recurrence").value("MONTHLY"))
                .andExpect(jsonPath("$.startDate").value("2026-10-03"))
                .andExpect(jsonPath("$.amount").value(9.99));

        assertThat(eventRepository.findByUserOrderByNextDueDateAsc(user)).hasSize(1);
        assertThat(detection.detect(user)).extracting(SubscriptionSuggestion::key).doesNotContain("netflix");
    }

    @Test
    void dismissingHidesASuggestionForThatUserOnly() throws Exception {
        mvc.perform(post("/api/subscriptions/suggestions/spotify/dismiss").header("Authorization", "Bearer " + token(user)))
                .andExpect(status().isNoContent());
        // dismissing twice is harmless
        mvc.perform(post("/api/subscriptions/suggestions/spotify/dismiss").header("Authorization", "Bearer " + token(user)))
                .andExpect(status().isNoContent());

        assertThat(detection.detect(user)).extracting(SubscriptionSuggestion::key).doesNotContain("spotify");

        User other = newUser();
        assertThat(detection.detect(other)).as("other user has no accounts").isEmpty();
    }

    @Test
    void suggestionsAreListedOverTheApi() throws Exception {
        mvc.perform(get("/api/subscriptions/suggestions").header("Authorization", "Bearer " + token(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.key=='netflix')].name").value("Netflix"));
    }

    @Test
    void acceptingSomethingThatWasNeverSuggestedIs404AndOddKeysAreRejected() throws Exception {
        mvc.perform(post("/api/subscriptions/suggestions/tesco/accept").header("Authorization", "Bearer " + token(user)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/subscriptions/suggestions/NET-FLIX1/accept").header("Authorization", "Bearer " + token(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pendingChargesAreIgnored() {
        User u = newUser();
        BankAccount a = new BankAccount();
        a.setUser(u); a.setPlaidAccessToken("t"); a.setPlaidItemId("i"); a.setPlaidAccountId("a-" + UUID.randomUUID()); a.setName("x");
        bankAccountRepository.save(a);
        for (String date : List.of("2026-08-01", "2026-09-01")) {
            Transaction tx = tx(a, "Hulu", "7.99", date);
            tx.setPending(true);
            transactionRepository.save(tx);
        }
        assertThat(detection.detect(u)).isEmpty();
    }

    @Test
    void findsYearlyChargesInTwoYearsOfHistory() {
        User u = newUser();
        BankAccount a = newAccount(u);
        chargeTo(a, "AMAZON PRIME", "79.00", "2024-10-01");
        chargeTo(a, "AMAZON PRIME", "95.00", "2025-10-01");             // 20% rise, still the same renewal
        chargeTo(a, "Old Insurer", "300.00", "2024-09-01", "2025-09-01"); // due 1 Sep this year, never came
        chargeTo(a, "Dentist", "60.00", "2025-03-14", "2025-09-02");     // twice, but not a year apart

        List<SubscriptionSuggestion> found = detection.detect(u);

        assertThat(found).extracting(SubscriptionSuggestion::key).containsExactly("amazon");
        SubscriptionSuggestion prime = found.get(0);
        assertThat(prime.recurrence()).isEqualTo(Recurrence.YEARLY);
        assertThat(prime.nextExpected()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(prime.confidence()).isEqualTo("MEDIUM");
    }

    @Test
    void monthlyChargesAreJudgedOnRecentMonthsSoAnOldPriceDoesNotHideThem() {
        User u = newUser();
        BankAccount a = newAccount(u);
        chargeTo(a, "Disney Plus", "4.99", "2025-12-10", "2026-01-10", "2026-02-10");
        chargeTo(a, "Disney Plus", "7.99", "2026-04-10", "2026-05-10", "2026-06-10", "2026-07-10", "2026-08-10", "2026-09-10");

        List<SubscriptionSuggestion> found = detection.detect(u);

        assertThat(found).singleElement().satisfies(disney -> {
            assertThat(disney.recurrence()).isEqualTo(Recurrence.MONTHLY);
            assertThat(disney.amount()).isEqualByComparingTo("7.99");
            assertThat(disney.occurrences()).isEqualTo(6);
        });
    }

    // --- helpers -------------------------------------------------------------

    private BankAccount newAccount(User owner) {
        BankAccount a = new BankAccount();
        a.setUser(owner);
        a.setPlaidAccessToken("t-" + UUID.randomUUID());
        a.setPlaidItemId("i-" + UUID.randomUUID());
        a.setPlaidAccountId("a-" + UUID.randomUUID());
        a.setName("Current");
        return bankAccountRepository.save(a);
    }

    private void chargeTo(BankAccount a, String name, String amount, String... dates) {
        for (String date : dates) {
            transactionRepository.save(tx(a, name, amount, date));
        }
    }

    private SubscriptionSuggestion find(String key) {
        return detection.detect(user).stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
    }

    private void charge(String name, String amount, String... dates) {
        for (String date : dates) {
            transactionRepository.save(tx(account, name, amount, date));
        }
    }

    private Transaction tx(BankAccount a, String name, String amount, String date) {
        Transaction tx = new Transaction();
        tx.setBankAccount(a);
        tx.setPlaidTransactionId(UUID.randomUUID().toString());
        tx.setName(name);
        tx.setAmount(new BigDecimal(amount));
        tx.setTransactionDate(LocalDate.parse(date));
        tx.setPending(false);
        return tx;
    }

    private User newUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@example.com");
        u.setPasswordHash("x");
        u.setFullName("Subscriptions Test");
        return userRepository.save(u);
    }

    private String token(User u) {
        return jwtService.generateToken(new UserPrincipal(u));
    }
}
