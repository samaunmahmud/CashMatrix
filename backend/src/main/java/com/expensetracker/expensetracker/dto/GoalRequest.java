package com.expensetracker.expensetracker.dto;

import com.expensetracker.expensetracker.model.Recurrence;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class GoalRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 60, message = "Name must be 60 characters or fewer")
    private String name;

    @Size(max = 16, message = "Pick a single emoji")
    private String emoji;

    @NotNull(message = "Target is required")
    @DecimalMin(value = "1.00", message = "Target must be at least 1")
    @Digits(integer = 10, fraction = 2, message = "Target can have at most 2 decimal places")
    private BigDecimal targetAmount;

    // Optional: when the user wants to have it by.
    private LocalDate targetDate;

    // Optional regular saving: an amount and how often (WEEKLY or MONTHLY), from a start date (default today).
    @DecimalMin(value = "0.01", message = "Regular amount must be more than zero")
    @Digits(integer = 10, fraction = 2, message = "Regular amount can have at most 2 decimal places")
    private BigDecimal planAmount;

    private Recurrence planFrequency;

    private LocalDate planStartDate;

    private boolean planAutoRecord;
}
