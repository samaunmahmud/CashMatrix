package com.expensetracker.expensetracker.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class BudgetRequest {

    @NotBlank(message = "Category is required")
    @Size(max = 60, message = "Category must be 60 characters or fewer")
    private String category;

    @NotNull(message = "Monthly limit is required")
    @DecimalMin(value = "0.01", message = "Monthly limit must be more than zero")
    @Digits(integer = 10, fraction = 2, message = "Monthly limit can have at most 2 decimal places")
    private BigDecimal monthlyLimit;
}
