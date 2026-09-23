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
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
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

    /** How far back an account's first import reaches (Plaid's maximum). */
    public static final int FULL_HISTORY_DAYS = 730;
    /** How far back later syncs look: far enough to pick up anything that was pending or posted late. */
    static final int RECENT_DAYS = 90;

    private final TransactionRepository transactionRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BankAccountService bankAccountService;
    private final PlaidService plaidService;
    private final TransactionAlertService alertService;
    private final Clock clock;

    /** A sync the user asked for: they are in the app, so any alerts it raises are shown there. */
    public int syncTransactions(User user) {
        return syncTransactions(user, true);
    }

    /**
     * Pulls transactions from Plaid, saving what is new, refreshes account balances, and raises
     * money in / money out alerts for new transactions (except on an account's first import).
     * A login with a newly connected account gets up to two years of history, the rest the last 90 days.
     */
    @SuppressWarnings("unchecked")
    public int syncTransactions(User user, boolean userIsPresent) {
        List<BankAccount> accounts = bankAccountRepository.findByUser(user);
        if (accounts.isEmpty()) return 0;

        LocalDate today = LocalDate.now(clock);
        String endDate = today.toString();

        Map<String, BankAccount> byPlaidId = accounts.stream()
                .collect(Collectors.toMap(BankAccount::getPlaidAccountId, Function.identity(), (a, b) -> a));

        // Several accounts at one bank share a login, so ask Plaid once per login.
        Set<String> accessTokens = accounts.stream()
                .map(BankAccount::getPlaidAccessToken)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int savedCount = 0;
        List<Transaction> alertable = new ArrayList<>();

        for (String accessToken : accessTokens) {
            boolean firstImport = accounts.stream()
                    .anyMatch(a -> a.getPlaidAccessToken().equals(accessToken) && a.getTransactionsSyncedAt() == null);
            String startDate = today.minusDays(firstImport ? FULL_HISTORY_DAYS : RECENT_DAYS).toString();
            int offset = 0;
            int total;
            do {
                Map result = plaidService.getTransactions(accessToken, startDate, endDate, offset);
                List<Map<String, Object>> transactions = (List<Map<String, Object>>) result.get("transactions");
                Object totalValue = result.get("total_transactions");
                total = totalValue instanceof Number number ? number.intValue() : transactions.size();

                for (Map<String, Object> tx : transactions) {
                    Transaction saved = saveIfNew(tx, byPlaidId);
                    if (saved == null) continue;
                    savedCount++;
                    if (saved.getBankAccount().getTransactionsSyncedAt() != null) alertable.add(saved);
                }

                if (transactions.isEmpty()) break; // nothing more to fetch, whatever the total says
                offset += transactions.size();
            } while (offset < total);

            refreshBalances(accessToken);
        }

        // An update query rather than saving the accounts: they were loaded before the balances were
        // refreshed, so saving them would put the old balances back.
        bankAccountRepository.markSynced(user, clock.instant());

        try {
            alertService.alert(user, alertable, userIsPresent);
        } catch (RuntimeException ex) {
            log.warn("Could not raise transaction alerts for user {}: {}", user.getId(), ex.getMessage());
        }
        return savedCount;
    }

    public List<Transaction> getTransactionsForUser(User user) {
        List<BankAccount> accounts = bankAccountRepository.findByUser(user);
        return transactionRepository.findByBankAccountInOrderByTransactionDateDesc(accounts);
    }

    /** @return the saved transaction, or null if it was already stored or isn't for one of the user's accounts */
    @SuppressWarnings("unchecked")
    private Transaction saveIfNew(Map<String, Object> tx, Map<String, BankAccount> byPlaidId) {
        String plaidTxId = (String) tx.get("transaction_id");
        if (transactionRepository.findByPlaidTransactionId(plaidTxId).isPresent()) {
            return null;
        }

        BankAccount account = byPlaidId.get((String) tx.get("account_id"));
        if (account == null) {
            return null; // belongs to an account this user has not saved
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

        return transactionRepository.save(transaction);
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
