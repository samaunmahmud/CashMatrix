package com.expensetracker.expensetracker.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Any of the settings; the ones left out stay as they are. */
@Getter
@Setter
public class NotificationSettingsRequest {

    private Boolean emailEnabled;

    private Boolean transactionAlertsEnabled;

    @DecimalMin(value = "0.00", message = "Alert amount can't be negative")
    @DecimalMax(value = "100000", message = "Alert amount can be at most 100,000")
    @Digits(integer = 6, fraction = 2, message = "Alert amount can have at most 2 decimal places")
    private BigDecimal transactionAlertMinimum;

    public boolean isEmpty() {
        return emailEnabled == null && transactionAlertsEnabled == null && transactionAlertMinimum == null;
    }
}
