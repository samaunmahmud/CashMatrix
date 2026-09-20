package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.config.PlaidConfig;
import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.repository.TransactionRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountsAndSyncTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired BankAccountRepository bankAccountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired TransactionService transactionService;
    @Autowired JwtService jwtService;
    @MockBean PlaidService plaidService;

    User user;
    BankAccount current;
    BankAccount savings;

    @BeforeEach
    void linkABankWithTwoAccounts() {
        user = saveUser();
        current = saveAccount(user, "acc-current-" + UUID.randomUUID(), "access-" + UUID.randomUUID());
        // Both accounts belong to one bank login, so they share an access token.
        savings = saveAccount(user, "acc-savings-" + UUID.randomUUID(), current.getPlaidAccessToken());
    }

    @Test
    void accountsEndpointShowsBalancesButNeverThePlaidToken() throws Exception {
        current.setCurrentBalance(new BigDecimal("2847.32"));
        current.setAvailableBalance(new BigDecimal("3347.32"));
        current.setCurrency("GBP");
        current.setMask("4821");
        bankAccountRepository.save(current);

        mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.mask=='4821')].currentBalance").value(2847.32))
                .andExpect(jsonPath("$[?(@.mask=='4821')].currency").value("GBP"))
                .andExpect(jsonPath("$[0].plaidAccessToken").doesNotExist())
                .andExpect(jsonPath("$[0].plaidItemId").doesNotExist());
    }

    @Test
    void usersOnlySeeTheirOwnAccounts() throws Exception {
        User other = saveUser();

        mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + tokenFor(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void syncFetchesEveryPageOnceForTheSharedLoginAndSkipsUnknownAccounts() {
        String token = current.getPlaidAccessToken();
        when(plaidService.getTransactions(eq(token), anyString(), anyString(), eq(0))).thenReturn(Map.of(
                "total_transactions", 4,
                "transactions", List.of(
                        tx("t1-" + token, current, 12.5), tx("t2-" + token, savings, 40.0))));
        when(plaidService.getTransactions(eq(token), anyString(), anyString(), eq(2))).thenReturn(Map.of(
                "total_transactions", 4,
                "transactions", List.of(
                        tx("t3-" + token, current, 3.2),
                        tx("t4-" + token, new BankAccount() {{ setPlaidAccountId("someone-elses"); }}, 9.99))));
        when(plaidService.getAccounts(token)).thenReturn(Map.of("accounts", List.of(
                Map.of("account_id", current.getPlaidAccountId(), "mask", "1111",
                        "balances", Map.of("current", 100.5, "available", 90, "iso_currency_code", "GBP")),
                Map.of("account_id", savings.getPlaidAccountId(), "mask", "2222",
                        "balances", Map.of("current", 5000, "iso_currency_code", "GBP")))));

        int saved = transactionService.syncTransactions(user);

        assertThat(saved).as("3 known transactions saved, the stranger's skipped").isEqualTo(3);
        verify(plaidService, times(1)).getTransactions(eq(token), anyString(), anyString(), eq(0));
        verify(plaidService, times(1)).getTransactions(eq(token), anyString(), anyString(), eq(2));
        verify(plaidService, never()).getTransactions(eq(token), anyString(), anyString(), eq(4));
        verify(plaidService, times(1)).getAccounts(token); // once per login, not once per account

        BankAccount refreshedCurrent = bankAccountRepository.findById(current.getId()).orElseThrow();
        assertThat(refreshedCurrent.getCurrentBalance()).isEqualByComparingTo("100.5");
        assertThat(refreshedCurrent.getAvailableBalance()).isEqualByComparingTo("90");
        assertThat(refreshedCurrent.getCurrency()).isEqualTo("GBP");
        assertThat(refreshedCurrent.getMask()).isEqualTo("1111");
        assertThat(refreshedCurrent.getBalanceUpdatedAt()).isNotNull();
        assertThat(bankAccountRepository.findById(savings.getId()).orElseThrow().getAvailableBalance()).isNull();
    }

    @Test
    void syncingTwiceDoesNotDuplicateTransactions() {
        String token = current.getPlaidAccessToken();
        when(plaidService.getTransactions(eq(token), anyString(), anyString(), eq(0))).thenReturn(Map.of(
                "total_transactions", 1,
                "transactions", List.of(tx("only-" + token, current, 7.0))));
        when(plaidService.getAccounts(token)).thenReturn(Map.of("accounts", List.of()));

        assertThat(transactionService.syncTransactions(user)).isEqualTo(1);
        assertThat(transactionService.syncTransactions(user)).isZero();
        assertThat(transactionRepository.findByBankAccountInOrderByTransactionDateDesc(List.of(current, savings))).hasSize(1);
    }

    @Test
    void aFailedBalanceRefreshDoesNotLoseTheSyncedTransactions() {
        String token = current.getPlaidAccessToken();
        when(plaidService.getTransactions(eq(token), anyString(), anyString(), eq(0))).thenReturn(Map.of(
                "total_transactions", 1,
                "transactions", List.of(tx("kept-" + token, current, 5.0))));
        when(plaidService.getAccounts(token)).thenThrow(new IllegalArgumentException("Plaid is down"));

        assertThat(transactionService.syncTransactions(user)).isEqualTo(1);
    }

    @Test
    void plaidCountryCodesDefaultToTheUkAndAcceptSeveral() {
        PlaidConfig config = new PlaidConfig();
        ReflectionTestUtils.setField(config, "countryCodes", "GB");
        assertThat(config.getCountryCodes()).containsExactly("GB");

        ReflectionTestUtils.setField(config, "countryCodes", " gb , us ,");
        assertThat(config.getCountryCodes()).containsExactly("GB", "US");
    }

    @Test
    void plaidBaseUrlFollowsTheEnvironmentUnlessOverridden() {
        PlaidConfig config = new PlaidConfig();
        ReflectionTestUtils.setField(config, "env", "sandbox");
        assertThat(config.getBaseUrl()).isEqualTo("https://sandbox.plaid.com");
        ReflectionTestUtils.setField(config, "env", "production");
        assertThat(config.getBaseUrl()).isEqualTo("https://production.plaid.com");

        ReflectionTestUtils.setField(config, "baseUrlOverride", " http://127.0.0.1:8099 ");
        assertThat(config.getBaseUrl()).isEqualTo("http://127.0.0.1:8099");
    }

    // --- helpers -------------------------------------------------------------

    private User saveUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@example.com");
        u.setPasswordHash("x");
        u.setFullName("Sync Test");
        return userRepository.save(u);
    }

    private BankAccount saveAccount(User owner, String plaidAccountId, String accessToken) {
        BankAccount a = new BankAccount();
        a.setUser(owner);
        a.setPlaidAccessToken(accessToken);
        a.setPlaidItemId("item-" + accessToken);
        a.setPlaidAccountId(plaidAccountId);
        a.setName("Account");
        return bankAccountRepository.save(a);
    }

    private String tokenFor(User u) {
        return jwtService.generateToken(new UserPrincipal(u));
    }

    private Map<String, Object> tx(String id, BankAccount account, double amount) {
        return Map.of(
                "transaction_id", id,
                "account_id", account.getPlaidAccountId(),
                "name", "Shop " + id,
                "amount", amount,
                "date", LocalDate.now().minusDays(3).toString(),
                "pending", false,
                "category", List.of("Shops", "Groceries"));
    }
}
