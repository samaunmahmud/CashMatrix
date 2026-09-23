package com.expensetracker.expensetracker.dto;

import com.expensetracker.expensetracker.model.Transaction;
import com.expensetracker.expensetracker.service.MerchantNames;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One transaction as the app shows it.
 *
 * @param amount   Plaid's sign convention: positive is money out, negative is money in
 * @param merchant a readable merchant name ("NETFLIX.COM 866-579" becomes "Netflix")
 * @param retailer the merchant without branch words ("Tesco Express" becomes "Tesco"), for spending by retailer
 */
public record TransactionResponse(
        Long id,
        Long accountId,
        String name,
        String merchant,
        String retailer,
        BigDecimal amount,
        String plaidCategory,
        String userCategory,
        LocalDate transactionDate,
        boolean pending
) {
    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(), tx.getBankAccount().getId(), tx.getName(), MerchantNames.display(tx.getName()),
                MerchantNames.retailer(tx.getName()),
                tx.getAmount(), tx.getPlaidCategory(), tx.getUserCategory(), tx.getTransactionDate(),
                Boolean.TRUE.equals(tx.getPending()));
    }
}
