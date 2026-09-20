package com.expensetracker.expensetracker.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationSettingsRequest {

    @NotNull(message = "emailEnabled is required")
    private Boolean emailEnabled;
}
