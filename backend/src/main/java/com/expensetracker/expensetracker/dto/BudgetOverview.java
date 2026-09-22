package com.expensetracker.expensetracker.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Every budget for one month, with totals, plus categories the user spends in
 * that don't have a budget yet.
 *
 * @param month       the month as yyyy-MM
 * @param daysLeft    days left in the month including today, or 0 for a past month
 * @param suggestions categories without a budget, busiest first, with a suggested limit
 */
public record BudgetOverview(
        String month,
        boolean currentMonth,
        int daysLeft,
        BigDecimal totalLimit,
        BigDecimal totalSpent,
        List<BudgetResponse> budgets,
        List<CategorySuggestion> suggestions
) {
    /** @param suggestedLimit typical monthly spend in the category, rounded up to a tidy figure */
    public record CategorySuggestion(String category, BigDecimal suggestedLimit) {}
}
