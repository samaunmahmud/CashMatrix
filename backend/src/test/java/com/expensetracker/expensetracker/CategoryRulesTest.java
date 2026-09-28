package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.Notification;
import com.expensetracker.expensetracker.model.Transaction;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.*;
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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfig.class)
class CategoryRulesTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired BankAccountRepository bankAccountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired CategoryRuleRepository ruleRepository;
    @Autowired BudgetRepository budgetRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired TransactionService transactionService;
    @MockBean PlaidService plaidService;

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
    }

    @Test
    void refilesOneTransactionAndAddsANote() throws Exception {
        Transaction pret = spend("PRET A MANGER 1234", "Restaurants", "8.40");
        Transaction other = spend("Pret A Manger Kings Cross", "Restaurants", "4.10");

        edit(pret, "{\"category\":\"  Work   lunch \",\"note\":\" Team breakfast \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.userCategory").value("Work lunch"))
                .andExpect(jsonPath("$.transaction.note").value("Team breakfast"))
                .andExpect(jsonPath("$.alsoUpdated").value(0));

        assertThat(reload(other).getUserCategory()).as("without a rule, only that transaction changes").isNull();
        assertThat(ruleRepository.findByUserOrderByMerchantNameAsc(user)).isEmpty();
    }

    @Test
    void anEmptyCategoryGoesBackToTheBanksOne() throws Exception {
        Transaction tx = spend("Uber", "Taxi", "12.00");
        tx.setUserCategory("Travel");
        tx.setNote("Airport");
        transactionRepository.save(tx);

        edit(tx, "{\"category\":\"\",\"note\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.userCategory").doesNotExist())
                .andExpect(jsonPath("$.transaction.plaidCategory").value("Taxi"))
                .andExpect(jsonPath("$.transaction.note").doesNotExist());
    }

    @Test
    void aRuleRefilesEveryTransactionFromTheRetailerAndNewOnesAsTheySync() throws Exception {
        Transaction express = spend("Tesco Express", "Supermarkets", "6.20");
        Transaction stores = spend("TESCO STORES 2041", "Supermarkets", "54.10");
        Transaction petrol = spend("Tesco Petrol", "Gas stations", "60.00");
        Transaction sainsburys = spend("Sainsbury's", "Supermarkets", "20.00");

        edit(express, "{\"category\":\"Groceries\",\"applyToRetailer\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alsoUpdated").value(2));

        assertThat(reload(stores).getUserCategory()).isEqualTo("Groceries");
        assertThat(reload(petrol).getUserCategory()).isEqualTo("Groceries");
        assertThat(reload(sainsburys).getUserCategory()).as("another retailer").isNull();

        mvc.perform(get("/api/category-rules").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].retailer").value("Tesco"))
                .andExpect(jsonPath("$[0].category").value("Groceries"));

        // A later sync brings in a new Tesco transaction, which follows the rule.
        String token = account.getPlaidAccessToken();
        account.setTransactionsSyncedAt(java.time.Instant.now());
        bankAccountRepository.save(account);
        when(plaidService.getTransactions(eq(token), anyString(), anyString(), eq(0))).thenReturn(Map.of(
                "total_transactions", 2,
                "transactions", List.of(
                        plaidTx("TESCO METRO 88", "Supermarkets"),
                        plaidTx("Boots", "Pharmacies"))));
        when(plaidService.getAccounts(token)).thenReturn(Map.of("accounts", List.of()));
        transactionService.syncTransactions(user);

        Map<String, String> byName = new java.util.HashMap<>();
        transactionRepository.findByBankAccountInOrderByTransactionDateDesc(List.of(account))
                .forEach(tx -> byName.put(tx.getName(), String.valueOf(tx.getUserCategory())));
        assertThat(byName).containsEntry("TESCO METRO 88", "Groceries").containsEntry("Boots", "null");
    }

    @Test
    void changingARuleUpdatesItAndAnEmptyCategoryRemovesIt() throws Exception {
        Transaction a = spend("Deliveroo", "Restaurants", "25.00");
        Transaction b = spend("DELIVEROO.CO.UK", "Restaurants", "18.00");

        edit(a, "{\"category\":\"Takeaway\",\"applyToRetailer\":true}").andExpect(jsonPath("$.alsoUpdated").value(1));
        edit(b, "{\"category\":\"Treats\",\"applyToRetailer\":true}").andExpect(jsonPath("$.alsoUpdated").value(1));
        assertThat(ruleRepository.findByUserOrderByMerchantNameAsc(user)).singleElement()
                .satisfies(rule -> assertThat(rule.getCategory()).isEqualTo("Treats"));

        edit(a, "{\"category\":\"\",\"applyToRetailer\":true}").andExpect(status().isOk());
        assertThat(ruleRepository.findByUserOrderByMerchantNameAsc(user)).isEmpty();
        assertThat(reload(b).getUserCategory()).as("back to the bank's category").isNull();
    }

    @Test
    void deletingARuleKeepsWhatItAlreadyFiled() throws Exception {
        Transaction a = spend("Netflix", "Entertainment", "10.99");
        Transaction b = spend("NETFLIX.COM 866-579", "Entertainment", "10.99");
        edit(a, "{\"category\":\"Subscriptions\",\"applyToRetailer\":true}").andExpect(status().isOk());
        Long ruleId = ruleRepository.findByUserOrderByMerchantNameAsc(user).get(0).getId();

        mvc.perform(delete("/api/category-rules/" + ruleId).header("Authorization", auth()))
                .andExpect(status().isNoContent());

        assertThat(ruleRepository.findById(ruleId)).isEmpty();
        assertThat(reload(b).getUserCategory()).isEqualTo("Subscriptions");
    }

    @Test
    void refilingIntoABudgetedCategoryCanRaiseABudgetAlert() throws Exception {
        com.expensetracker.expensetracker.model.Budget budget = new com.expensetracker.expensetracker.model.Budget();
        budget.setUser(user);
        budget.setCategory("Gifts");
        budget.setMonthlyLimit(new BigDecimal("50"));
        budgetRepository.save(budget);
        Transaction tx = spend("John Lewis", "Department stores", "70.00");

        edit(tx, "{\"category\":\"gifts\"}").andExpect(status().isOk());

        List<Notification> alerts = notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(user);
        assertThat(alerts).singleElement().satisfies(n -> assertThat(n.getTitle()).isEqualTo("Over your Gifts budget"));
    }

    @Test
    void usersCanOnlyEditTheirOwnTransactionsAndRules() throws Exception {
        Transaction mine = spend("Tesco", "Supermarkets", "5.00");
        edit(mine, "{\"category\":\"Groceries\",\"applyToRetailer\":true}").andExpect(status().isOk());
        Long ruleId = ruleRepository.findByUserOrderByMerchantNameAsc(user).get(0).getId();

        User stranger = newUser();
        String strangerAuth = "Bearer " + jwtService.generateToken(new UserPrincipal(stranger));
        mvc.perform(put("/api/transactions/" + mine.getId()).header("Authorization", strangerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"Hacked\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/category-rules/" + ruleId).header("Authorization", strangerAuth))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/category-rules").header("Authorization", strangerAuth))
                .andExpect(jsonPath("$").isEmpty());

        assertThat(reload(mine).getUserCategory()).isEqualTo("Groceries");
    }

    @Test
    void rejectsOverlongValues() throws Exception {
        Transaction tx = spend("Tesco", "Supermarkets", "5.00");
        edit(tx, "{\"category\":\"" + "x".repeat(61) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.category").value("Category must be 60 characters or fewer"));
        edit(tx, "{\"note\":\"" + "x".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.note").value("Note must be 200 characters or fewer"));
    }

    private ResultActions edit(Transaction tx, String json) throws Exception {
        return mvc.perform(put("/api/transactions/" + tx.getId()).header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Transaction reload(Transaction tx) {
        return transactionRepository.findById(tx.getId()).orElseThrow();
    }

    private Map<String, Object> plaidTx(String name, String category) {
        return Map.of(
                "transaction_id", UUID.randomUUID().toString(),
                "account_id", account.getPlaidAccountId(),
                "name", name,
                "amount", 9.99,
                "date", FixedClockTestConfig.TODAY,
                "pending", false,
                "category", List.of("Shops", category));
    }

    private Transaction spend(String name, String plaidCategory, String amount) {
        Transaction tx = new Transaction();
        tx.setBankAccount(account);
        tx.setPlaidTransactionId(UUID.randomUUID().toString());
        tx.setName(name);
        tx.setPlaidCategory(plaidCategory);
        tx.setAmount(new BigDecimal(amount));
        tx.setTransactionDate(LocalDate.parse("2026-09-10"));
        return transactionRepository.save(tx);
    }

    private User newUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@example.com");
        u.setPasswordHash("x");
        u.setFullName("Rules Test");
        return userRepository.save(u);
    }

    private String auth() {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(user));
    }
}
