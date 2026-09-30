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
 * @param forecast               where this month's spending is heading, or null without enough history
 */
public record SpendingInsights(
        List<MonthTotal> months,
        BigDecimal spentSoFar,
        BigDecimal spentSameTimeLastMonth,
        List<CategoryChange> categories,
        LocalDate historyStart,
        Forecast forecast
) {
    /** @param partial true for the current month, and for a month the synced history only partly covers */
    public record MonthTotal(String month, BigDecimal spent, BigDecimal moneyIn, boolean partial) {}

    public record CategoryChange(String category, BigDecimal thisMonth, BigDecimal lastMonth) {}

    /**
     * @param total          expected spending for the whole month
     * @param billsToCome    calendar payments and subscriptions due after today
     * @param everydayToCome everyday spending expected over the days left, at everydayPerDay
     */
    public record Forecast(BigDecimal total, BigDecimal spentSoFar, BigDecimal billsToCome, int billCount,
                           BigDecimal everydayToCome, BigDecimal everydayPerDay, int daysLeft, LocalDate monthEnd) {}
}
