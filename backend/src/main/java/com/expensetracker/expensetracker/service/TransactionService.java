package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.Transaction;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final BankAccountRepository bankAccountRepository;
    private final PlaidService plaidService;

    @SuppressWarnings("unchecked")
    public int syncTransactions(User user) {
        List<BankAccount> accounts = bankAccountRepository.findByUser(user);
        if (accounts.isEmpty()) return 0;

        String endDate = LocalDate.now().toString();
        String startDate = LocalDate.now().minusDays(90).toString();

        int savedCount = 0;

        for (BankAccount account : accounts) {
            Map result = plaidService.getTransactions(
                    account.getPlaidAccessToken(), startDate, endDate);

            List<Map<String, Object>> transactions =
                    (List<Map<String, Object>>) result.get("transactions");

            for (Map<String, Object> tx : transactions) {
                String plaidTxId = (String) tx.get("transaction_id");

                if (transactionRepository.findByPlaidTransactionId(plaidTxId).isPresent()) {
                    continue;
                }

                String txAccountId = (String) tx.get("account_id");
                BankAccount matchedAccount = accounts.stream()
                        .filter(a -> a.getPlaidAccountId().equals(txAccountId))
                        .findFirst()
                        .orElse(account);

                Transaction transaction = new Transaction();
                transaction.setBankAccount(matchedAccount);
                transaction.setPlaidTransactionId(plaidTxId);
                transaction.setName((String) tx.get("name"));
                transaction.setAmount(new BigDecimal(tx.get("amount").toString()));
                transaction.setTransactionDate(LocalDate.parse((String) tx.get("date")));
                transaction.setPending(Boolean.TRUE.equals(tx.get("pending")));

                List<String> categories = (List<String>) tx.get("category");
                if (categories != null && !categories.isEmpty()) {
                    transaction.setPlaidCategory(categories.get(categories.size() - 1));
                }

                transactionRepository.save(transaction);
                savedCount++;
            }
        }

        return savedCount;
    }

    public List<Transaction> getTransactionsForUser(User user) {
        List<BankAccount> accounts = bankAccountRepository.findByUser(user);
        return transactionRepository
                .findByBankAccountInOrderByTransactionDateDesc(accounts);
    }
}