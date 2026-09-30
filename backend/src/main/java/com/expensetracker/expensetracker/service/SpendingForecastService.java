package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.dto.SpendingInsights.Forecast;
import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.CalendarEvent;
import com.expensetracker.expensetracker.model.EventType;
import com.expensetracker.expensetracker.model.Transaction;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.CalendarEventRepository;
import com.expensetracker.expensetracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Where this month's spending is heading: what has gone already, the bills on the calendar
 * that are still to come, and everyday spending for the days left at the user's usual pace.
 *
 * The usual pace comes from the last few complete months, leaving out the charges for things on
 * the calendar so the bills aren't counted twice. Without a complete month to go on, this month's
 * own pace is used once there is a week of it.
 */
@Service
@RequiredArgsConstructor
public class SpendingForecastService {

    static final int PACE_MONTHS = 3;
    static final int MIN_DAYS_OF_OWN_PACE = 7;

    private final TransactionRepository transactionRepository;
    private final CalendarEventRepository eventRepository;

    /** @return the forecast, or null when there isn't enough history to make one */
    Forecast forecast(User user, List<BankAccount> accounts, LocalDate today) {
        if (accounts.isEmpty()) return null;
        YearMonth thisMonth = YearMonth.from(today);
        LocalDate monthEnd = thisMonth.atEndOfMonth();
        List<Transaction> transactions = transactionRepository.findByBankAccountInAndTransactionDateBetween(
                accounts, thisMonth.minusMonths(PACE_MONTHS).atDay(1), today);
        if (transactions.isEmpty()) return null;

        List<CalendarEvent> bills = eventRepository.findByUserOrderByNextDueDateAsc(user).stream()
                .filter(e -> e.getType() == EventType.PAYMENT || e.getType() == EventType.SUBSCRIPTION)
                .filter(e -> e.getAmount() != null && e.getAmount().signum() > 0 && e.isActive())
                .toList();
        Set<String> billWords = bills.stream()
                .flatMap(e -> BillMatcher.words(e.getTitle()).stream())
                .collect(Collectors.toSet());
        Set<BigDecimal> billAmounts = bills.stream()
                .map(e -> e.getAmount().stripTrailingZeros())
                .collect(Collectors.toSet());

        LocalDate historyStart = transactions.stream().map(Transaction::getTransactionDate).min(LocalDate::compareTo).orElseThrow();
        List<Transaction> everyday = transactions.stream()
                .filter(Spending::isSpending)
                .filter(tx -> !isBill(tx, billWords, billAmounts))
                .toList();

        BigDecimal perDay = usualPace(everyday, thisMonth, historyStart, today);
        if (perDay == null) return null;

        BigDecimal spentSoFar = Spending.total(transactions.stream()
                .filter(tx -> YearMonth.from(tx.getTransactionDate()).equals(thisMonth))
                .toList());

        // Bills due from tomorrow: one due today may or may not have gone out yet, and is most
        // likely already in what has been spent.
        LocalDate tomorrow = today.plusDays(1);
        BigDecimal billsToCome = BigDecimal.ZERO;
        int billCount = 0;
        for (CalendarEvent bill : bills) {
            LocalDate from = bill.getNextDueDate().isAfter(tomorrow) ? bill.getNextDueDate() : tomorrow;
            if (from.isAfter(monthEnd)) continue;
            List<LocalDate> due = bill.getRecurrence().isRecurring()
                    ? bill.getRecurrence().occurrencesBetween(bill.getStartDate(), from, monthEnd)
                    : (bill.getNextDueDate().isBefore(from) ? List.of() : List.of(bill.getNextDueDate()));
            billsToCome = billsToCome.add(bill.getAmount().multiply(BigDecimal.valueOf(due.size())));
            billCount += due.size();
        }

        int daysLeft = monthEnd.getDayOfMonth() - today.getDayOfMonth();
        BigDecimal everydayToCome = perDay.multiply(BigDecimal.valueOf(daysLeft));
        BigDecimal total = spentSoFar.add(billsToCome).add(everydayToCome).setScale(2, RoundingMode.HALF_UP);
        return new Forecast(total, spentSoFar, billsToCome, billCount,
                everydayToCome.setScale(2, RoundingMode.HALF_UP), perDay, daysLeft, monthEnd);
    }

    /**
     * Whether a charge is most likely one of the calendar's bills: the same amount, or a name that shares
     * its merchant word with a bill's title ("Rent" and "HARTLEY LETTINGS RENT", "Netflix" and "NETFLIX.COM").
     */
    private static boolean isBill(Transaction tx, Set<String> billWords, Set<BigDecimal> billAmounts) {
        if (billAmounts.contains(tx.getAmount().stripTrailingZeros())) return true;
        return BillMatcher.sharesWord(billWords, tx.getName());
    }

    /** Everyday spending per day over the complete months the history covers, or this month's own. */
    private static BigDecimal usualPace(List<Transaction> everyday, YearMonth thisMonth, LocalDate historyStart, LocalDate today) {
        List<YearMonth> complete = new ArrayList<>();
        for (int back = PACE_MONTHS; back >= 1; back--) {
            YearMonth month = thisMonth.minusMonths(back);
            if (!historyStart.isAfter(month.atDay(1))) complete.add(month);
        }
        if (!complete.isEmpty()) {
            LocalDate from = complete.get(0).atDay(1);
            LocalDate to = complete.get(complete.size() - 1).atEndOfMonth();
            BigDecimal spent = Spending.total(everyday.stream()
                    .filter(tx -> !tx.getTransactionDate().isBefore(from) && !tx.getTransactionDate().isAfter(to))
                    .toList());
            long days = to.toEpochDay() - from.toEpochDay() + 1;
            return spent.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        }
        LocalDate from = historyStart.isAfter(thisMonth.atDay(1)) ? historyStart : thisMonth.atDay(1);
        long days = today.toEpochDay() - from.toEpochDay() + 1;
        if (days < MIN_DAYS_OF_OWN_PACE) return null;
        BigDecimal spent = Spending.total(everyday.stream()
                .filter(tx -> !tx.getTransactionDate().isBefore(from))
                .toList());
        return spent.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }
}
