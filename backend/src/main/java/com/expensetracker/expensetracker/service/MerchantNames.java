package com.expensetracker.expensetracker.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns the raw names banks put on transactions ("NETFLIX.COM 866-579-7172", "PureGym Ltd")
 * into a merchant: a short key for grouping and a readable name for showing.
 */
public final class MerchantNames {

    // A web address ending, with any path after it ("APPLE.COM/BILL")
    private static final Pattern DOMAIN_SUFFIX = Pattern.compile("\\.(com|co\\.uk|uk|net|org|io)\\b(/\\S*)?", Pattern.CASE_INSENSITIVE);
    private static final Set<String> COMPANY_SUFFIXES = Set.of("ltd", "limited", "plc", "inc", "llc");
    private static final Set<String> NOISE = Set.of(
            "the", "card", "payment", "purchase", "pos", "dd", "direct", "debit", "www", "http", "https",
            "com", "uk", "ltd", "limited", "plc", "inc", "llc", "gbp", "visa", "recurring", "subscription",
            "monthly", "standing", "order", "faster", "payments", "bill", "online", "web");
    private static final Set<String> BRANCH_WORDS = Set.of(
            "store", "stores", "superstore", "express", "extra", "metro", "local", "petrol", "branch");

    private MerchantNames() {
    }

    /**
     * First meaningful word of a merchant name, lower-case letters only. Null if there isn't one.
     * Coarse, but it copes with the reference numbers banks tack on ("NETFLIX.COM 866-579", "Netflix 12345").
     */
    public static String key(String name) {
        if (name == null) return null;
        return Arrays.stream(DOMAIN_SUFFIX.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("").split("[^a-z]+"))
                .filter(token -> token.length() >= 3 && !NOISE.contains(token))
                .findFirst()
                .orElse(null);
    }

    /**
     * A readable name: domain suffixes, company suffixes and reference numbers dropped. Shouting names
     * ("DISNEY PLUS") become title case, but names that already have their own capitalisation
     * ("PureGym", "Spotify AB") are left as the merchant wrote them.
     */
    public static String display(String raw) {
        if (raw == null) return "";
        List<String> words = new ArrayList<>();
        for (String word : DOMAIN_SUFFIX.matcher(raw).replaceAll("").trim().split("\\s+")) {
            if (word.matches(".*\\d.*")) break; // everything from the first reference number on is noise
            if (word.isBlank() || COMPANY_SUFFIXES.contains(word.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", ""))) continue;
            boolean shouting = word.equals(word.toUpperCase(Locale.ROOT)) && word.length() > 4; // short capitals are usually initials: HMRC, ASDA
            words.add(shouting ? Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT) : word);
        }
        String name = String.join(" ", words).trim();
        return name.isEmpty() ? raw.trim() : name;
    }

    /**
     * The shop behind a name, for adding up spending by retailer: the {@link #display} name without the
     * words banks add for the kind of branch ("Tesco Express", "Sainsbury's Local" and "TESCO STORES 2041"
     * are all "Tesco" or "Sainsbury's"). Branch locations ("Dishoom Kings Cross") can't be told apart from
     * a name on their own, so the app merges those when it sees the shorter name too.
     */
    public static String retailer(String raw) {
        List<String> words = new ArrayList<>(Arrays.asList(display(raw).split("\\s+")));
        while (words.size() > 1 && BRANCH_WORDS.contains(words.get(words.size() - 1).toLowerCase(Locale.ROOT))) {
            words.remove(words.size() - 1);
        }
        return String.join(" ", words);
    }
}
