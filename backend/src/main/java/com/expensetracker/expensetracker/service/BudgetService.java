package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.config.ResourceNotFoundException;
import com.expensetracker.expensetracker.dto.BudgetOverview;
import com.expensetracker.expensetracker.dto.BudgetOverview.CategorySuggestion;
import com.expensetracker.expensetracker.dto.BudgetRequest;
import com.expensetracker.expensetracker.dto.BudgetResponse;
import com.expensetracker.expensetracker.dto.BudgetResponse.BudgetStatus;
import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.Budget;
import com.expensetracker.expensetracker.model.Transaction;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.repository.BudgetRepository;
import com.expensetracker.expensetracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BudgetService {

    static final int MAX_BUDGETS = 30;
    static final BigDecimal NEAR_LIMIT_SHARE = new BigDecimal("0.80");
    // How many earlier months a suggested limit is based on.
    static final int SUGGESTION_MONTHS = 3;
    static final int MAX_SUGGESTIONS = 8;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal SUGGESTION_STEP = BigDecimal.valueOf(5);

    private final BudgetRepository budgetRepository;
    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BudgetOverview overview(User user, YearMonth month) {
        YearMonth thisMonth = YearMonth.now(clock);
        if (month.isAfter(thisMonth)) {
            throw new IllegalArgumentException("Budgets can only be shown up to the current month");
        }

        List<Budget> budgets = budgetRepository.findByUserOrderByCategoryAsc(user);
        Map<String, BigDecimal> spent = spendingByCategory(user, month);

        List<BudgetResponse> responses = budgets.stream().map(budget -> toResponse(budget, spent)).toList();
        BigDecimal totalLimit = responses.stream().map(BudgetResponse::monthlyLimit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpent = responses.stream().map(BudgetResponse::spent).reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean current = month.equals(thisMonth);
        int daysLeft = current ? month.lengthOfMonth() - LocalDate.now(clock).getDayOfMonth() + 1 : 0;

        return new BudgetOverview(month.toString(), current, daysLeft, totalLimit, totalSpent, responses,
                suggestions(user, budgets, thisMonth));
    }

    @Transactional
    public BudgetResponse create(User user, BudgetRequest request) {
        String category = tidy(request.getCategory());
        if (budgetRepository.existsByUserAndCategoryIgnoreCase(user, category)) {
            throw new IllegalArgumentException("You already have a budget for " + category);
        }
        if (budgetRepository.findByUserOrderByCategoryAsc(user).size() >= MAX_BUDGETS) {
            throw new IllegalArgumentException("You can have up to " + MAX_BUDGETS + " budgets");
        }

        Budget budget = new Budget();
        budget.setUser(user);
        budget.setCategory(category);
        budget.setMonthlyLimit(request.getMonthlyLimit());
        budgetRepository.save(budget);
        return toResponse(budget, spendingByCategory(user, YearMonth.now(clock)));
    }

    @Transactional
    public BudgetResponse update(User user, Long id, BudgetRequest request) {
        Budget budget = find(user, id);
        String category = tidy(request.getCategory());
        if (budgetRepository.existsByUserAndCategoryIgnoreCaseAndIdNot(user, category, id)) {
            throw new IllegalArgumentException("You already have a budget for " + category);
        }
        budget.setCategory(category);
        budget.setMonthlyLimit(request.getMonthlyLimit());
        return toResponse(budget, spendingByCategory(user, YearMonth.now(clock)));
    }

    @Transactional
    public void delete(User user, Long id) {
        budgetRepository.delete(find(user, id));
    }

    /** Spending per category in one month. Keys ignore case. */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> spendingByCategory(User user, YearMonth month) {
        return Spending.byCategory(transactionsBetween(user, month.atDay(1), month.atEndOfMonth()));
    }

    static BudgetStatus statusOf(BigDecimal spent, BigDecimal limit) {
        if (spent.compareTo(limit) > 0) return BudgetStatus.OVER;
        if (spent.compareTo(limit.multiply(NEAR_LIMIT_SHARE)) >= 0) return BudgetStatus.NEAR_LIMIT;
        return BudgetStatus.ON_TRACK;
    }

    private BudgetResponse toResponse(Budget budget, Map<String, BigDecimal> spentByCategory) {
        BigDecimal limit = budget.getMonthlyLimit();
        BigDecimal spent = spentByCategory.getOrDefault(budget.getCategory(), BigDecimal.ZERO);
        int percent = spent.multiply(HUNDRED).divide(limit, 0, RoundingMode.HALF_UP).intValue();
        return new BudgetResponse(budget.getId(), budget.getCategory(), limit, spent,
                limit.subtract(spent), percent, statusOf(spent, limit));
    }

    /**
     * Categories the user spent in recently that have no budget yet. The suggested limit is
     * their average over the last few complete months the synced history fully covers; a
     * month cut short by the start of the history, or still under way, would understate it.
     * Someone with no complete month yet gets this month so far instead.
     */
    private List<CategorySuggestion> suggestions(User user, List<Budget> budgets, YearMonth thisMonth) {
        LocalDate from = thisMonth.minusMonths(SUGGESTION_MONTHS).atDay(1);
        List<Transaction> recent = transactionsBetween(user, from, thisMonth.atEndOfMonth());
        if (recent.isEmpty()) return List.of();

        LocalDate historyStart = recent.stream().map(Transaction::getTransactionDate).min(LocalDate::compareTo).orElseThrow();
        YearMonth firstFull = historyStart.getDayOfMonth() == 1 ? YearMonth.from(historyStart) : YearMonth.from(historyStart).plusMonths(1);
        YearMonth start = firstFull.isAfter(thisMonth.minusMonths(SUGGESTION_MONTHS)) ? firstFull : thisMonth.minusMonths(SUGGESTION_MONTHS);

        List<Transaction> basis;
        long monthCount;
        if (start.isBefore(thisMonth)) {
            basis = recent.stream().filter(tx -> {
                YearMonth month = YearMonth.from(tx.getTransactionDate());
                return !month.isBefore(start) && month.isBefore(thisMonth);
            }).toList();
            monthCount = start.until(thisMonth, ChronoUnit.MONTHS);
        } else {
            basis = recent.stream().filter(tx -> YearMonth.from(tx.getTransactionDate()).equals(thisMonth)).toList();
            monthCount = 1;
        }

        Set<String> budgeted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        budgets.forEach(budget -> budgeted.add(budget.getCategory()));
        BigDecimal months = BigDecimal.valueOf(monthCount);

        return Spending.byCategory(basis).entrySet().stream()
                .filter(entry -> !budgeted.contains(entry.getKey()))
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(MAX_SUGGESTIONS)
                .map(entry -> new CategorySuggestion(entry.getKey(), roundUp(entry.getValue().divide(months, 2, RoundingMode.HALF_UP))))
                .toList();
    }

    /** Up to the next 5, so a suggestion reads as a budget someone would pick (82.40 becomes 85). */
    private static BigDecimal roundUp(BigDecimal amount) {
        BigDecimal steps = amount.divide(SUGGESTION_STEP, 0, RoundingMode.CEILING);
        return steps.max(BigDecimal.ONE).multiply(SUGGESTION_STEP);
    }

    private List<Transaction> transactionsBetween(User user, LocalDate from, LocalDate to) {
        List<BankAccount> accounts = bankAccountRepository.findByUser(user);
        if (accounts.isEmpty()) return List.of();
        return transactionRepository.findByBankAccountInAndTransactionDateBetween(accounts, from, to);
    }

    private Budget find(User user, Long id) {
        return budgetRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found"));
    }

    /** Trims and collapses inner spaces so "  Eating   out " and "Eating out" are one budget. */
    private static String tidy(String category) {
        return category.trim().replaceAll("\\s+", " ");
    }
}
