package com.expensetracker.expensetracker.dto;

/**
 * @param alsoUpdated how many other transactions from the same retailer were re-filed too
 */
public record TransactionUpdateResponse(TransactionResponse transaction, int alsoUpdated) {
}
