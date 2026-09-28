package com.expensetracker.expensetracker.dto;

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
}
