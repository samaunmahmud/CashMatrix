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
        String link,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getEvent() != null ? n.getEvent().getId() : null, n.getTitle(), n.getMessage(),
                n.getDueDate(), n.openPath(), n.isRead(), n.getCreatedAt());
    }
}
