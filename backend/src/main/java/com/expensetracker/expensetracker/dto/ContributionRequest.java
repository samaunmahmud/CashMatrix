package com.expensetracker.expensetracker.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Money added to a goal, or taken out of it when negative. */
@Getter
@Setter
public class ContributionRequest {

    @NotNull(message = "Amount is required")
    @Digits(integer = 10, fraction = 2, message = "Amount can have at most 2 decimal places")
    private BigDecimal amount;
}
