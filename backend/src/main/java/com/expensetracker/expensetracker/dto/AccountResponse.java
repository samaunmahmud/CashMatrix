package com.expensetracker.expensetracker.dto;

import com.expensetracker.expensetracker.model.BankAccount;

import java.math.BigDecimal;
import java.time.Instant;

/** A linked account as the app shows it. The Plaid access token is deliberately not part of this. */
public record AccountResponse(
        Long id,
        String name,
        String officialName,
        String type,
        String subtype,
        String mask,
        BigDecimal currentBalance,
        BigDecimal availableBalance,
        String currency,
        Instant balanceUpdatedAt
) {
    public static AccountResponse from(BankAccount a) {
        return new AccountResponse(
                a.getId(), a.getName(), a.getOfficialName(), a.getType(), a.getSubtype(), a.getMask(),
                a.getCurrentBalance(), a.getAvailableBalance(), a.getCurrency(), a.getBalanceUpdatedAt());
    }
}
