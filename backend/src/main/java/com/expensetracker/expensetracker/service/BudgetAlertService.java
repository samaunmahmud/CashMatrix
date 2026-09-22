package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.dto.BudgetResponse.BudgetStatus;
import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.Budget;
import com.expensetracker.expensetracker.model.Notification;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.repository.BudgetRepository;
import com.expensetracker.expensetracker.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Warns a user when a budget is nearly used up (80%) and again when it is overspent.
 *
 * Each warning is raised at most once per budget per month (the alert key is unique),
 * and once a budget is over there is no point also saying it is nearly used up.
 */
@Service
@RequiredArgsConstructor
public class BudgetAlertService {

    private final BudgetRepository budgetRepository;
    private final BudgetService budgetService;
    private final NotificationRepository notificationRepository;
    private final BankAccountRepository bankAccountRepository;
    private final Clock clock;

    /** Checks every user's budgets. Called by the scheduler. */
    @Transactional
    public int checkAll() {
        Map<User, List<Budget>> byUser = budgetRepository.findAllWithUser().stream()
                .collect(Collectors.groupingBy(Budget::getUser, LinkedHashMap::new, Collectors.toList()));
        int created = 0;
        for (Map.Entry<User, List<Budget>> entry : byUser.entrySet()) {
            created += check(entry.getKey(), entry.getValue(), false);
        }
        return created;
    }

    /** Checks one user's budgets. Called when they sync or open their alerts. */
    @Transactional
    public int check(User user) {
        return check(user, budgetRepository.findByUserOrderByCategoryAsc(user), true);
    }

    private int check(User user, List<Budget> budgets, boolean userIsPresent) {
        if (budgets.isEmpty()) return 0;

        LocalDate today = LocalDate.now(clock);
        YearMonth month = YearMonth.from(today);
        Map<String, BigDecimal> spent = budgetService.spendingByCategory(user, month);
        String currency = currencyOf(user);

        int created = 0;
        for (Budget budget : budgets) {
            BigDecimal amount = spent.getOrDefault(budget.getCategory(), BigDecimal.ZERO);
            BudgetStatus status = BudgetService.statusOf(amount, budget.getMonthlyLimit());
            if (status == BudgetStatus.ON_TRACK) continue;

            String overKey = key(budget, month, BudgetStatus.OVER);
            String key = key(budget, month, status);
            if (notificationRepository.existsByAlertKey(key)) continue;
            if (status == BudgetStatus.NEAR_LIMIT && notificationRepository.existsByAlertKey(overKey)) {
                continue; // a refund took it back under the limit; it has already been flagged as over
            }

            Notification notification = build(user, budget, amount, status, key, today, currency);
            if (userIsPresent) {
                // They are in the app right now and will see it there.
                notification.setEmailSent(true);
                notification.setPushSent(true);
            }
            notificationRepository.save(notification);
            created++;
        }
        return created;
    }

    private static Notification build(User user, Budget budget, BigDecimal spent, BudgetStatus status,
                                      String key, LocalDate today, String currency) {
        String category = budget.getCategory();
        String monthName = today.getMonth().getDisplayName(TextStyle.FULL, Locale.UK);
        String used = money(spent, currency) + " of your " + money(budget.getMonthlyLimit(), currency)
                + " " + category + " budget for " + monthName;

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setAlertKey(key);
        notification.setLink("/budgets");
        notification.setDueDate(today);
        if (status == BudgetStatus.OVER) {
            notification.setTitle("Over your " + category + " budget");
            notification.setMessage("You've spent " + used + ", "
                    + money(spent.subtract(budget.getMonthlyLimit()), currency) + " over.");
        } else {
            notification.setTitle(category + " budget nearly used");
            notification.setMessage("You've spent " + used + ". "
                    + money(budget.getMonthlyLimit().subtract(spent), currency) + " left.");
        }
        return notification;
    }

    private static String key(Budget budget, YearMonth month, BudgetStatus status) {
        return "budget-" + budget.getId() + "-" + month + "-" + (status == BudgetStatus.OVER ? "over" : "near");
    }

    /** The currency of the user's accounts, or pounds if they don't say. */
    private String currencyOf(User user) {
        return bankAccountRepository.findByUser(user).stream()
                .map(BankAccount::getCurrency)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("GBP");
    }

    private static String money(BigDecimal amount, String currency) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.UK);
        try {
            format.setCurrency(Currency.getInstance(currency));
        } catch (IllegalArgumentException ex) {
            // An unknown code from the bank: fall back to pounds rather than failing the alert.
        }
        return format.format(amount);
    }
}
