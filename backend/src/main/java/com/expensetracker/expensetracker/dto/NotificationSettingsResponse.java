package com.expensetracker.expensetracker.dto;

import java.math.BigDecimal;

/** What the Settings screen needs to know about reminders and alerts. */
public record NotificationSettingsResponse(
        boolean emailEnabled,
        boolean emailAvailable,
        boolean pushAvailable,
        String pushPublicKey,
        long pushDevices,
        boolean transactionAlertsEnabled,
        BigDecimal transactionAlertMinimum
) {
}
