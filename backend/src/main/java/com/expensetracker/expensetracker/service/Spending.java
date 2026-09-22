package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.model.Transaction;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * The rules for what counts as spending, shared by budgets and insights so they
 * always agree with each other (and with the chart on the home screen).
 *
 * Plaid reports money leaving an account as a positive amount and money arriving
 * as a negative one. Pending transactions count: the money is already spoken for.
 */
final class Spending {

    static final String UNCATEGORISED = "Uncategorised";

    private Spending() {
    }

    static boolean isSpending(Transaction tx) {
        return tx.getAmount().signum() > 0;
    }

    /** The user's own category if they set one, otherwise the bank's. */
    static String categoryOf(Transaction tx) {
        if (tx.getUserCategory() != null && !tx.getUserCategory().isBlank()) return tx.getUserCategory().trim();
        if (tx.getPlaidCategory() != null && !tx.getPlaidCategory().isBlank()) return tx.getPlaidCategory().trim();
        return UNCATEGORISED;
    }

    static BigDecimal total(Collection<Transaction> transactions) {
        return transactions.stream()
                .filter(Spending::isSpending)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal moneyIn(Collection<Transaction> transactions) {
        return transactions.stream()
                .filter(tx -> tx.getAmount().signum() < 0)
                .map(tx -> tx.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Spending per category. Keys ignore case, so "Groceries" and "groceries" are one category. */
    static Map<String, BigDecimal> byCategory(Collection<Transaction> transactions) {
        Map<String, BigDecimal> totals = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Transaction tx : transactions) {
            if (isSpending(tx)) {
                totals.merge(categoryOf(tx), tx.getAmount(), BigDecimal::add);
            }
        }
        return totals;
    }
}
