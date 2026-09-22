package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.dto.SpendingInsights;
import com.expensetracker.expensetracker.dto.SpendingInsights.CategoryChange;
import com.expensetracker.expensetracker.dto.SpendingInsights.MonthTotal;
import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.Transaction;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Month-by-month spending for the trend chart, and how this month compares with the last.
 *
 * Only synced transactions can be counted, so months before the earliest one are left out
 * rather than shown as zero, and a month the history only partly covers is marked partial.
 */
@Service
@RequiredArgsConstructor
public class SpendingInsightsService {

    static final int MAX_MONTHS = 12;
    static final int MAX_CATEGORIES = 8;

    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SpendingInsights insights(User user, int monthCount) {
        if (monthCount < 1 || monthCount > MAX_MONTHS) {
            throw new IllegalArgumentException("Months must be between 1 and " + MAX_MONTHS);
        }

        LocalDate today = LocalDate.now(clock);
        YearMonth thisMonth = YearMonth.from(today);
        YearMonth lastMonth = thisMonth.minusMonths(1);
        YearMonth firstMonth = thisMonth.minusMonths(monthCount - 1L);
        // Reach back at least one month for the this-month-against-last comparison.
        LocalDate from = (firstMonth.isBefore(lastMonth) ? firstMonth : lastMonth).atDay(1);

        List<BankAccount> accounts = bankAccountRepository.findByUser(user);
        List<Transaction> transactions = accounts.isEmpty() ? List.of()
                : transactionRepository.findByBankAccountInAndTransactionDateBetween(accounts, from, thisMonth.atEndOfMonth());
        if (transactions.isEmpty()) {
            return new SpendingInsights(List.of(), BigDecimal.ZERO, null, List.of(), null);
        }

        LocalDate historyStart = transactions.stream().map(Transaction::getTransactionDate).min(LocalDate::compareTo).orElseThrow();
        Map<YearMonth, List<Transaction>> byMonth = transactions.stream()
                .collect(Collectors.groupingBy(tx -> YearMonth.from(tx.getTransactionDate())));

        List<MonthTotal> months = new ArrayList<>();
        YearMonth historyMonth = YearMonth.from(historyStart);
        for (YearMonth month = firstMonth.isBefore(historyMonth) ? historyMonth : firstMonth;
             !month.isAfter(thisMonth); month = month.plusMonths(1)) {
            List<Transaction> inMonth = byMonth.getOrDefault(month, List.of());
            boolean partial = month.equals(thisMonth) || (month.equals(historyMonth) && historyStart.getDayOfMonth() > 1);
            months.add(new MonthTotal(month.toString(), Spending.total(inMonth), Spending.moneyIn(inMonth), partial));
        }

        List<Transaction> thisMonthTx = byMonth.getOrDefault(thisMonth, List.of());
        List<Transaction> lastMonthTx = byMonth.getOrDefault(lastMonth, List.of());

        BigDecimal spentSoFar = Spending.total(thisMonthTx.stream()
                .filter(tx -> !tx.getTransactionDate().isAfter(today)).toList());

        // "Same time last month" only means something if the history covers all of last month up to that day.
        BigDecimal sameTimeLastMonth = null;
        if (!historyStart.isAfter(lastMonth.atDay(1))) {
            LocalDate sameDay = lastMonth.atDay(Math.min(today.getDayOfMonth(), lastMonth.lengthOfMonth()));
            sameTimeLastMonth = Spending.total(lastMonthTx.stream()
                    .filter(tx -> !tx.getTransactionDate().isAfter(sameDay)).toList());
        }

        return new SpendingInsights(months, spentSoFar, sameTimeLastMonth,
                categoryChanges(thisMonthTx, lastMonthTx), historyStart);
    }

    private static List<CategoryChange> categoryChanges(List<Transaction> thisMonth, List<Transaction> lastMonth) {
        Map<String, BigDecimal> now = Spending.byCategory(thisMonth);
        Map<String, BigDecimal> before = Spending.byCategory(lastMonth);

        Set<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        categories.addAll(now.keySet());
        categories.addAll(before.keySet());

        return categories.stream()
                .map(category -> new CategoryChange(category,
                        now.getOrDefault(category, BigDecimal.ZERO),
                        before.getOrDefault(category, BigDecimal.ZERO)))
                .sorted(Comparator.comparing(CategoryChange::thisMonth)
                        .thenComparing(CategoryChange::lastMonth).reversed())
                .limit(MAX_CATEGORIES)
                .toList();
    }
}
