package com.expensetracker.expensetracker.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/** Money as it reads in alert text: "£1,234.50". */
public final class Money {

    public static final String DEFAULT_CURRENCY = "GBP";

    private Money() {
    }

    public static String format(BigDecimal amount, String currency) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.UK);
        try {
            format.setCurrency(Currency.getInstance(currency != null ? currency : DEFAULT_CURRENCY));
        } catch (IllegalArgumentException ex) {
            // An unknown code from the bank: fall back to pounds rather than failing the alert.
        }
        return format.format(amount);
    }
}
