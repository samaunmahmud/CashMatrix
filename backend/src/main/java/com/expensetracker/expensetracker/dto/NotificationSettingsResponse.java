package com.expensetracker.expensetracker.dto;

/** What the Settings screen needs to know about reminders. */
public record NotificationSettingsResponse(
        boolean emailEnabled,
        boolean emailAvailable,
        boolean pushAvailable,
        String pushPublicKey,
        long pushDevices
) {
}
