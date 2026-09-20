package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.Transaction;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    static final int HISTORY_DAYS = 90;

    private final TransactionRepository transactionRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BankAccountService bankAccountService;
    private final PlaidService plaidService;

    /** Pulls the last 90 days from Plaid, saving what is new, and refreshes account balances. */
    @SuppressWarnings("unchecked")
    public int syncTransactions(User user) {
        List<BankAccount> accounts = bankAccountRepository.findByUser(user);
        if (accounts.isEmpty()) return 0;

        String endDate = LocalDate.now().toString();
        String startDate = LocalDate.now().minusDays(HISTORY_DAYS).toString();

        Map<String, BankAccount> byPlaidId = accounts.stream()
                .collect(Collectors.toMap(BankAccount::getPlaidAccountId, Function.identity(), (a, b) -> a));

        // Several accounts at one bank share a login, so ask Plaid once per login.
        Set<String> accessTokens = accounts.stream()
                .map(BankAccount::getPlaidAccessToken)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int savedCount = 0;

        for (String accessToken : accessTokens) {
            int offset = 0;
            int total;
            do {
                Map result = plaidService.getTransactions(accessToken, startDate, endDate, offset);
                List<Map<String, Object>> transactions = (List<Map<String, Object>>) result.get("transactions");
                Object totalValue = result.get("total_transactions");
                total = totalValue instanceof Number number ? number.intValue() : transactions.size();

                for (Map<String, Object> tx : transactions) {
                    if (saveIfNew(tx, byPlaidId)) savedCount++;
                }

                if (transactions.isEmpty()) break; // nothing more to fetch, whatever the total says
                offset += transactions.size();
            } while (offset < total);

            refreshBalances(accessToken);
        }

        return savedCount;
    }

    public List<Transaction> getTransactionsForUser(User user) {
        List<BankAccount> accounts = bankAccountRepository.findByUser(user);
        return transactionRepository.findByBankAccountInOrderByTransactionDateDesc(accounts);
    }

    @SuppressWarnings("unchecked")
    private boolean saveIfNew(Map<String, Object> tx, Map<String, BankAccount> byPlaidId) {
        String plaidTxId = (String) tx.get("transaction_id");
        if (transactionRepository.findByPlaidTransactionId(plaidTxId).isPresent()) {
            return false;
        }

        BankAccount account = byPlaidId.get((String) tx.get("account_id"));
        if (account == null) {
            return false; // belongs to an account this user has not saved
        }

        Transaction transaction = new Transaction();
        transaction.setBankAccount(account);
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
        return true;
    }

    // Balances are a bonus on top of the sync: if Plaid can't supply them right now,
    // the transactions that were just saved are still good.
    private void refreshBalances(String accessToken) {
        try {
            bankAccountService.updateBalances(plaidService.getAccounts(accessToken));
        } catch (RuntimeException ex) {
            log.warn("Could not refresh account balances: {}", ex.getMessage());
        }
    }
}
