package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;

    @SuppressWarnings("unchecked")
    public void saveAccounts(User user, String accessToken, String itemId, Map accountsResult) {
        List<Map<String, Object>> accounts =
                (List<Map<String, Object>>) accountsResult.get("accounts");

        for (Map<String, Object> account : accounts) {
            String plaidAccountId = (String) account.get("account_id");

            boolean alreadyExists = bankAccountRepository
                    .findByPlaidAccountId(plaidAccountId)
                    .isPresent();

            if (alreadyExists) continue;

            BankAccount bankAccount = new BankAccount();
            bankAccount.setUser(user);
            bankAccount.setPlaidAccessToken(accessToken);
            bankAccount.setPlaidItemId(itemId);
            bankAccount.setPlaidAccountId(plaidAccountId);
            bankAccount.setName((String) account.get("name"));
            bankAccount.setOfficialName((String) account.get("official_name"));
            bankAccount.setType(account.get("type") != null
                    ? account.get("type").toString() : null);
            bankAccount.setSubtype(account.get("subtype") != null
                    ? account.get("subtype").toString() : null);

            bankAccountRepository.save(bankAccount);
        }
    }

    public List<BankAccount> getAccountsForUser(User user) {
        return bankAccountRepository.findByUser(user);
    }
}