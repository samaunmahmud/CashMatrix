package com.expensetracker.expensetracker.dto;

import com.expensetracker.expensetracker.model.EventType;
import com.expensetracker.expensetracker.model.Recurrence;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class CalendarEventRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 120, message = "Title must be 120 characters or fewer")
    private String title;

    @Size(max = 1000, message = "Description must be 1000 characters or fewer")
    private String description;

    @NotNull(message = "Type is required (TASK, PAYMENT or SUBSCRIPTION)")
    private EventType type;

    @DecimalMin(value = "0.00", message = "Amount can't be negative")
    @Digits(integer = 10, fraction = 2, message = "Amount can have at most 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Date is required")
    private LocalDate startDate;

    // Optional: defaults to NONE (one-off).
    private Recurrence recurrence;

    // Optional: defaults to 1 day before.
    @Min(value = 0, message = "Reminder can't be negative")
    @Max(value = 30, message = "Reminder can be at most 30 days before")
    private Integer remindDaysBefore;
}
