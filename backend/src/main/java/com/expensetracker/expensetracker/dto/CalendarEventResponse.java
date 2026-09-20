package com.expensetracker.expensetracker.dto;

import com.expensetracker.expensetracker.model.CalendarEvent;
import com.expensetracker.expensetracker.model.EventType;
import com.expensetracker.expensetracker.model.Recurrence;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An item as the user created it (one row per item, however often it repeats). */
public record CalendarEventResponse(
        Long id,
        String title,
        String description,
        EventType type,
        BigDecimal amount,
        LocalDate startDate,
        LocalDate nextDueDate,
        Recurrence recurrence,
        int remindDaysBefore,
        boolean completed
) {
    public static CalendarEventResponse from(CalendarEvent e) {
        return new CalendarEventResponse(
                e.getId(), e.getTitle(), e.getDescription(), e.getType(), e.getAmount(),
                e.getStartDate(), e.getNextDueDate(), e.getRecurrence(),
                e.getRemindDaysBefore(), e.isCompleted());
    }
}
