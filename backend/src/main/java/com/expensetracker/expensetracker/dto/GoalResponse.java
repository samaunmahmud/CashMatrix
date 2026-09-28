package com.expensetracker.expensetracker.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A savings goal and how it is going.
 *
 * @param percentSaved  saved as a whole percentage of the target, at most 100
 * @param monthlyNeeded what to put aside each month from now to reach the target by its date;
 *                      empty when there is no date or the goal is reached
 * @param recent        the latest few changes to the saved amount, newest first
 */
public record GoalResponse(
        Long id,
        String name,
        String emoji,
        BigDecimal targetAmount,
        BigDecimal savedAmount,
        BigDecimal remaining,
        int percentSaved,
        LocalDate targetDate,
        BigDecimal monthlyNeeded,
        GoalStatus status,
        List<Contribution> recent
) {
    public enum GoalStatus { REACHED, IN_PROGRESS, PAST_DATE }

    public record Contribution(Long id, BigDecimal amount, LocalDate madeOn) {
    }
}
