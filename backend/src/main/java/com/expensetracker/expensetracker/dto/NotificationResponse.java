package com.expensetracker.expensetracker.dto;

import com.expensetracker.expensetracker.model.Notification;

import java.time.Instant;
import java.time.LocalDate;

public record NotificationResponse(
        Long id,
        Long eventId,
        String title,
        String message,
        LocalDate dueDate,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getEvent().getId(), n.getTitle(), n.getMessage(),
                n.getDueDate(), n.isRead(), n.getCreatedAt());
    }
}
