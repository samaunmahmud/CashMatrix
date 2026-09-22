package com.expensetracker.expensetracker.dto;

import java.math.BigDecimal;

/**
 * One budget and how it is doing in the month asked about.
 *
 * @param remaining   what is left to spend; negative once over the limit
 * @param percentUsed spent as a whole percentage of the limit (can go past 100)
 */
public record BudgetResponse(
        Long id,
        String category,
        BigDecimal monthlyLimit,
        BigDecimal spent,
        BigDecimal remaining,
        int percentUsed,
        BudgetStatus status
) {
    public enum BudgetStatus { ON_TRACK, NEAR_LIMIT, OVER }
}
