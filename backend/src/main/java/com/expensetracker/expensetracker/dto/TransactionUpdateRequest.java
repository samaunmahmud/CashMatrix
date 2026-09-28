package com.expensetracker.expensetracker.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Changes to one transaction.
 *
 * @param category       the user's own category; empty goes back to the bank's
 * @param applyToRetailer also use this category for every other transaction from the same retailer, now and in future
 */
@Getter
@Setter
public class TransactionUpdateRequest {

    @Size(max = 60, message = "Category must be 60 characters or fewer")
    private String category;

    @Size(max = 200, message = "Note must be 200 characters or fewer")
    private String note;

    private boolean applyToRetailer;
}
