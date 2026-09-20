package com.expensetracker.expensetracker.dto;

import com.expensetracker.expensetracker.model.Recurrence;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A charge that looks like a subscription, found in the user's bank transactions. */
public record SubscriptionSuggestion(
        String key,
        String name,
        BigDecimal amount,
        Recurrence recurrence,
        LocalDate lastCharged,
        LocalDate nextExpected,
        int occurrences,
        String confidence
) {
}
