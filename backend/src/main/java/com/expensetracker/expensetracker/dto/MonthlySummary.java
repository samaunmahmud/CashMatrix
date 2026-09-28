package com.expensetracker.expensetracker.dto;

import com.expensetracker.expensetracker.dto.BudgetResponse.BudgetStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A look back at one month.
 *
 * @param complete        false for the month still under way
 * @param previousSpent   spending in the month before, if the history covers it; empty otherwise
 * @param changePercent   spending against the month before, as a whole percentage (negative is less)
 * @param dailyAverage    spending per day over the days counted (so far, for this month)
 * @param savedToGoals    money put into savings goals during the month, less any taken out
 */
public record MonthlySummary(
        String month,
        boolean complete,
        int transactionCount,
        BigDecimal spent,
        BigDecimal moneyIn,
        BigDecimal net,
        BigDecimal dailyAverage,
        BigDecimal previousSpent,
        Integer changePercent,
        List<CategoryTotal> topCategories,
        List<Purchase> biggestPurchases,
        List<BudgetResult> budgets,
        BigDecimal savedToGoals
) {
    public record CategoryTotal(String category, BigDecimal amount, int share) {
    }

    public record Purchase(String merchant, String category, BigDecimal amount, LocalDate date) {
    }

    public record BudgetResult(String category, BigDecimal monthlyLimit, BigDecimal spent, BudgetStatus status) {
    }
}
