package com.expensetracker.expensetracker.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Tells whether a bank charge is for a bill on the calendar, by the merchant words they share:
 * "Rent" and "HARTLEY LETTINGS RENT", "Netflix" and "NETFLIX.COM 866-579".
 */
final class BillMatcher {

    private BillMatcher() {
    }

    /** The meaningful words of a name, as {@link MerchantNames#key} picks them. */
    static List<String> words(String name) {
        List<String> words = new ArrayList<>();
        if (name == null) return words;
        for (String part : name.split("\\s+")) {
            String key = MerchantNames.key(part);
            if (key != null) words.add(key);
        }
        return words;
    }

    static boolean sharesWord(Collection<String> billWords, String chargeName) {
        return words(chargeName).stream().anyMatch(billWords::contains);
    }
}
