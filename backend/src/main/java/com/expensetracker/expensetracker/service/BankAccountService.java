package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;

    /** Saves the accounts Plaid returned after a bank was linked, and records their balances. */
    @SuppressWarnings("unchecked")
    public void saveAccounts(User user, String accessToken, String itemId, Map accountsResult) {
        List<Map<String, Object>> accounts =
                (List<Map<String, Object>>) accountsResult.get("accounts");

        for (Map<String, Object> account : accounts) {
            String plaidAccountId = (String) account.get("account_id");

            // Linking the same bank again must not create duplicates, but it is a good
            // moment to refresh what we know about the account.
            BankAccount bankAccount = bankAccountRepository.findByPlaidAccountId(plaidAccountId)
                    .orElseGet(BankAccount::new);

            if (bankAccount.getId() == null) {
                bankAccount.setUser(user);
                bankAccount.setPlaidAccessToken(accessToken);
                bankAccount.setPlaidItemId(itemId);
                bankAccount.setPlaidAccountId(plaidAccountId);
                bankAccount.setName((String) account.get("name"));
                bankAccount.setOfficialName((String) account.get("official_name"));
                bankAccount.setType(account.get("type") != null ? account.get("type").toString() : null);
                bankAccount.setSubtype(account.get("subtype") != null ? account.get("subtype").toString() : null);
            }
            applyBalances(bankAccount, account);
            bankAccountRepository.save(bankAccount);
        }
    }

    /** Updates balances of accounts we already store, from a fresh Plaid /accounts/get response. */
    @SuppressWarnings("unchecked")
    public void updateBalances(Map accountsResult) {
        List<Map<String, Object>> accounts =
                (List<Map<String, Object>>) accountsResult.get("accounts");
        if (accounts == null) return;

        for (Map<String, Object> account : accounts) {
            bankAccountRepository.findByPlaidAccountId((String) account.get("account_id")).ifPresent(bankAccount -> {
                applyBalances(bankAccount, account);
                bankAccountRepository.save(bankAccount);
            });
        }
    }

    public List<BankAccount> getAccountsForUser(User user) {
        return bankAccountRepository.findByUser(user);
    }

    @SuppressWarnings("unchecked")
    private void applyBalances(BankAccount bankAccount, Map<String, Object> account) {
        if (account.get("mask") != null) {
            bankAccount.setMask(account.get("mask").toString());
        }
        Map<String, Object> balances = (Map<String, Object>) account.get("balances");
        if (balances == null) return;

        bankAccount.setCurrentBalance(toMoney(balances.get("current")));
        bankAccount.setAvailableBalance(toMoney(balances.get("available")));
        if (balances.get("iso_currency_code") != null) {
            bankAccount.setCurrency(balances.get("iso_currency_code").toString());
        }
        bankAccount.setBalanceUpdatedAt(Instant.now());
    }

    private BigDecimal toMoney(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }
}
