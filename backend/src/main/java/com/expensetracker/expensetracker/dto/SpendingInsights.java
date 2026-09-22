package com.expensetracker.expensetracker.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Spending month by month, and this month against last month.
 *
 * @param months                 oldest first, ending with the current month
 * @param spentSoFar             spent this month up to today
 * @param spentSameTimeLastMonth spent last month up to the same day, or null if the synced
 *                               history doesn't reach back far enough to say
 * @param categories             this month against last month, biggest first
 * @param historyStart           the earliest synced transaction, or null if there are none
 */
public record SpendingInsights(
        List<MonthTotal> months,
        BigDecimal spentSoFar,
        BigDecimal spentSameTimeLastMonth,
        List<CategoryChange> categories,
        LocalDate historyStart
) {
    /** @param partial true for the current month, and for a month the synced history only partly covers */
    public record MonthTotal(String month, BigDecimal spent, BigDecimal moneyIn, boolean partial) {}

    public record CategoryChange(String category, BigDecimal thisMonth, BigDecimal lastMonth) {}
}
