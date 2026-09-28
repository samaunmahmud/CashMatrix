package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.dto.BudgetResponse.BudgetStatus;
import com.expensetracker.expensetracker.dto.MonthlySummary;
import com.expensetracker.expensetracker.dto.MonthlySummary.BudgetResult;
import com.expensetracker.expensetracker.dto.MonthlySummary.CategoryTotal;
import com.expensetracker.expensetracker.dto.MonthlySummary.Purchase;
import com.expensetracker.expensetracker.model.*;
import com.expensetracker.expensetracker.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * "Your September": what came in and went out in a month, where it went, how budgets and goals
 * did. Early each month a summary of the month before goes to everyone who hasn't switched it off,
 * as an alert (and by email and push when those are on).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlySummaryService {

    static final int TOP_CATEGORIES = 5;
    static final int BIGGEST_PURCHASES = 3;
    // A summary that turns up after the first week of the month is old news, so it isn't sent.
    static final int SEND_BY_DAY = 7;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final GoalContributionRepository contributionRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MonthlySummary summary(User user, YearMonth month) {
        LocalDate today = LocalDate.now(clock);
        YearMonth thisMonth = YearMonth.from(today);
        if (month.isAfter(thisMonth)) throw new IllegalArgumentException("That month hasn't happened yet");

        List<BankAccount> accounts = bankAccountRepository.findByUser(user);
        YearMonth previous = month.minusMonths(1);
        List<Transaction> both = accounts.isEmpty() ? List.of()
                : transactionRepository.findByBankAccountInAndTransactionDateBetween(accounts, previous.atDay(1), month.atEndOfMonth());
        List<Transaction> inMonth = both.stream().filter(tx -> YearMonth.from(tx.getTransactionDate()).equals(month)).toList();
        List<Transaction> inPrevious = both.stream().filter(tx -> YearMonth.from(tx.getTransactionDate()).equals(previous)).toList();

        BigDecimal spent = Spending.total(inMonth);
        BigDecimal moneyIn = Spending.moneyIn(inMonth);
        boolean complete = month.isBefore(thisMonth);
        int days = complete ? month.lengthOfMonth() : today.getDayOfMonth();
        BigDecimal dailyAverage = spent.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);

        // Last month only compares fairly if the history starts on or before its first day.
        BigDecimal previousSpent = null;
        Integer changePercent = null;
        Optional<LocalDate> earliest = accounts.isEmpty() ? Optional.empty()
                : transactionRepository.findByBankAccountInOrderByTransactionDateDesc(accounts).stream()
                .map(Transaction::getTransactionDate).min(LocalDate::compareTo);
        if (earliest.isPresent() && !earliest.get().isAfter(previous.atDay(1).plusDays(3)) && !inPrevious.isEmpty()) {
            previousSpent = Spending.total(inPrevious);
            if (previousSpent.signum() > 0) {
                changePercent = spent.subtract(previousSpent).multiply(HUNDRED)
                        .divide(previousSpent, 0, RoundingMode.HALF_UP).intValue();
            }
        }

        Map<String, BigDecimal> byCategory = Spending.byCategory(inMonth);
        List<CategoryTotal> top = byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(TOP_CATEGORIES)
                .map(e -> new CategoryTotal(e.getKey(), e.getValue(), share(e.getValue(), spent)))
                .toList();

        List<Purchase> biggest = inMonth.stream()
                .filter(Spending::isSpending)
                .sorted(Comparator.comparing(Transaction::getAmount).reversed())
                .limit(BIGGEST_PURCHASES)
                .map(tx -> new Purchase(MerchantNames.display(tx.getName()), Spending.categoryOf(tx), tx.getAmount(), tx.getTransactionDate()))
                .toList();

        List<BudgetResult> budgets = budgetRepository.findByUserOrderByCategoryAsc(user).stream()
                .map(b -> {
                    BigDecimal used = byCategory.getOrDefault(b.getCategory(), BigDecimal.ZERO);
                    return new BudgetResult(b.getCategory(), b.getMonthlyLimit(), used, BudgetService.statusOf(used, b.getMonthlyLimit()));
                })
                .toList();

        BigDecimal saved = contributionRepository.totalForUserBetween(user, month.atDay(1), month.atEndOfMonth());

        return new MonthlySummary(month.toString(), complete, inMonth.size(), spent, moneyIn, moneyIn.subtract(spent),
                dailyAverage, previousSpent, changePercent, top, biggest, budgets, saved);
    }

    /**
     * Sends last month's summary to everyone who has one coming, once each. Called by the scheduler,
     * which runs hourly, so it only acts in the first week of the month.
     *
     * @return how many summaries were created
     */
    @Transactional
    public int sendMonthlySummaries() {
        LocalDate today = LocalDate.now(clock);
        if (today.getDayOfMonth() > SEND_BY_DAY) return 0;
        YearMonth month = YearMonth.from(today).minusMonths(1);

        int created = 0;
        for (User user : bankAccountRepository.findUsersWithAccounts()) {
            try {
                if (send(user, month)) created++;
            } catch (RuntimeException ex) {
                log.warn("Could not create the monthly summary for user {}: {}", user.getId(), ex.getMessage());
            }
        }
        return created;
    }

    private boolean send(User user, YearMonth month) {
        boolean wanted = preferenceRepository.findByUser(user).map(NotificationPreference::isMonthlySummaryEnabled).orElse(true);
        if (!wanted) return false;
        String key = "summary-" + user.getId() + "-" + month;
        if (notificationRepository.existsByAlertKey(key)) return false;

        MonthlySummary summary = summary(user, month);
        if (summary.transactionCount() == 0) return false;

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setAlertKey(key);
        notification.setLink("/summary?month=" + month);
        notification.setDueDate(LocalDate.now(clock));
        notification.setTitle("Your " + monthName(month) + " summary");
        notification.setMessage(message(summary, month, currencyOf(user)));
        notificationRepository.save(notification);
        return true;
    }

    /** The summary in a few lines, as the alert, push and email show it. */
    static String message(MonthlySummary summary, YearMonth month, String currency) {
        List<String> lines = new ArrayList<>();
        String spentLine = "You spent " + Money.format(summary.spent(), currency) + " in " + monthName(month);
        if (summary.changePercent() != null && summary.changePercent() != 0) {
            int change = summary.changePercent();
            spentLine += ", " + Math.abs(change) + "% " + (change < 0 ? "less" : "more") + " than in " + monthName(month.minusMonths(1));
        }
        lines.add(spentLine + ".");
        if (summary.moneyIn().signum() > 0) lines.add("Money in: " + Money.format(summary.moneyIn(), currency) + ".");
        if (!summary.topCategories().isEmpty()) {
            lines.add("Most went on " + summary.topCategories().stream().limit(3)
                    .map(c -> c.category() + " (" + Money.format(c.amount(), currency) + ")")
                    .collect(Collectors.joining(", ")) + ".");
        }
        if (!summary.budgets().isEmpty()) {
            long kept = summary.budgets().stream().filter(b -> b.status() != BudgetStatus.OVER).count();
            lines.add("Budgets: " + kept + " of " + summary.budgets().size() + " kept.");
        }
        if (summary.savedToGoals().signum() > 0) {
            lines.add("You saved " + Money.format(summary.savedToGoals(), currency) + " towards your goals.");
        }
        return String.join("\n", lines);
    }

    private static int share(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? 0 : part.multiply(HUNDRED).divide(whole, 0, RoundingMode.HALF_UP).intValue();
    }

    private static String monthName(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.UK);
    }

    private String currencyOf(User user) {
        return bankAccountRepository.findByUser(user).stream()
                .map(BankAccount::getCurrency)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(Money.DEFAULT_CURRENCY);
    }
}
