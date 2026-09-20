package com.expensetracker.expensetracker.dto;

import com.expensetracker.expensetracker.model.EventType;
import com.expensetracker.expensetracker.model.Recurrence;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One occurrence on a specific day, which is what a calendar view draws. */
public record CalendarEntryResponse(
        Long eventId,
        String title,
        String description,
        EventType type,
        BigDecimal amount,
        LocalDate date,
        Recurrence recurrence,
        boolean completed
) {
}
