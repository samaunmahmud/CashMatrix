package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.config.ResourceNotFoundException;
import com.expensetracker.expensetracker.dto.CalendarEventRequest;
import com.expensetracker.expensetracker.dto.CalendarEventResponse;
import com.expensetracker.expensetracker.dto.SubscriptionSuggestion;
import com.expensetracker.expensetracker.model.*;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.repository.CalendarEventRepository;
import com.expensetracker.expensetracker.repository.DismissedSuggestionRepository;
import com.expensetracker.expensetracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Spots subscriptions in a user's transactions: the same merchant charging about the
 * same amount on a steady weekly or monthly rhythm.
 *
 * It can only see what has been synced (90 days), so it recognises weekly and monthly
 * charges but not yearly ones. A merchant is grouped by the first meaningful word of
 * its name, which is coarse but copes with the reference numbers banks tack on
 * ("NETFLIX.COM 866-579", "Netflix 12345").
 */
@Service
@RequiredArgsConstructor
public class SubscriptionDetectionService {

    static final int STALE_AFTER_DAYS = 7;
    static final int DEFAULT_REMIND_DAYS = 2;
    // Subscriptions creep up by pennies or a price rise; a wilder spread is a shop, not a subscription.
    static final BigDecimal MAX_AMOUNT_SPREAD = new BigDecimal("1.15");

    private static final Pattern KEY_FORMAT = Pattern.compile("[a-z]{3,40}");
    private static final Pattern DOMAIN_SUFFIX = Pattern.compile("\\.(com|co\\.uk|uk|net|org|io)\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> COMPANY_SUFFIXES = Set.of("ltd", "limited", "plc", "inc", "llc");
    private static final Set<String> NOISE = Set.of(
            "the", "card", "payment", "purchase", "pos", "dd", "direct", "debit", "www", "http", "https",
            "com", "uk", "ltd", "limited", "plc", "inc", "llc", "gbp", "visa", "recurring", "subscription",
            "monthly", "standing", "order", "faster", "payments", "bill", "online", "web");

    private final TransactionRepository transactionRepository;
    private final BankAccountRepository bankAccountRepository;
    private final CalendarEventRepository eventRepository;
    private final DismissedSuggestionRepository dismissedRepository;
    private final CalendarService calendarService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SubscriptionSuggestion> detect(User user) {
        List<BankAccount> accounts = bankAccountRepository.findByUser(user);
        if (accounts.isEmpty()) {
            return List.of();
        }

        Set<String> ignored = dismissedRepository.findByUser(user).stream()
                .map(DismissedSuggestion::getMerchantKey)
                .collect(Collectors.toCollection(HashSet::new));
        // Already on the calendar, so there is nothing left to suggest.
        eventRepository.findByUserOrderByNextDueDateAsc(user).stream()
                .map(event -> merchantKey(event.getTitle()))
                .filter(Objects::nonNull)
                .forEach(ignored::add);

        LocalDate today = LocalDate.now(clock);
        Map<String, List<Transaction>> byMerchant = transactionRepository
                .findByBankAccountInOrderByTransactionDateDesc(accounts).stream()
                .filter(tx -> tx.getAmount().signum() > 0)            // money out only
                .filter(tx -> !Boolean.TRUE.equals(tx.getPending()))  // pending ones may still change
                .filter(tx -> merchantKey(tx.getName()) != null)
                .collect(Collectors.groupingBy(tx -> merchantKey(tx.getName())));

        return byMerchant.entrySet().stream()
                .filter(entry -> !ignored.contains(entry.getKey()))
                .map(entry -> analyse(entry.getKey(), entry.getValue(), today))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(SubscriptionSuggestion::nextExpected)
                        .thenComparing(SubscriptionSuggestion::name))
                .toList();
    }

    /** Adds a suggestion to the calendar as a subscription. The details come from our own analysis, never the client. */
    @Transactional
    public CalendarEventResponse accept(User user, String key) {
        SubscriptionSuggestion suggestion = detect(user).stream()
                .filter(s -> s.key().equals(requireValidKey(key)))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Suggestion not found"));

        CalendarEventRequest request = new CalendarEventRequest();
        request.setTitle(suggestion.name());
        request.setType(EventType.SUBSCRIPTION);
        request.setAmount(suggestion.amount());
        request.setStartDate(suggestion.nextExpected());
        request.setRecurrence(suggestion.recurrence());
        request.setRemindDaysBefore(DEFAULT_REMIND_DAYS);
        return calendarService.create(user, request);
    }

    @Transactional
    public void dismiss(User user, String key) {
        String valid = requireValidKey(key);
        if (!dismissedRepository.existsByUserAndMerchantKey(user, valid)) {
            DismissedSuggestion dismissed = new DismissedSuggestion();
            dismissed.setUser(user);
            dismissed.setMerchantKey(valid);
            dismissedRepository.save(dismissed);
        }
    }

    // ---- analysis ------------------------------------------------------------

    private Optional<SubscriptionSuggestion> analyse(String key, List<Transaction> transactions, LocalDate today) {
        List<Transaction> sorted = transactions.stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDate))
                .toList();
        if (sorted.size() < 2) {
            return Optional.empty();
        }

        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            gaps.add(ChronoUnit.DAYS.between(sorted.get(i - 1).getTransactionDate(), sorted.get(i).getTransactionDate()));
        }
        List<Long> orderedGaps = gaps.stream().sorted().toList();
        long median = orderedGaps.get(orderedGaps.size() / 2);

        Recurrence recurrence;
        int tolerance;
        int minimumCharges;
        if (median >= 6 && median <= 8) {
            recurrence = Recurrence.WEEKLY;
            tolerance = 2;
            minimumCharges = 3;   // two weekly charges prove very little
        } else if (median >= 27 && median <= 34) {
            recurrence = Recurrence.MONTHLY;
            tolerance = 4;
            minimumCharges = 2;
        } else {
            return Optional.empty();
        }

        if (sorted.size() < minimumCharges) {
            return Optional.empty();
        }
        for (long gap : gaps) {
            if (Math.abs(gap - median) > tolerance) {
                return Optional.empty(); // not a steady rhythm
            }
        }

        List<BigDecimal> amounts = sorted.stream().map(Transaction::getAmount).sorted().toList();
        BigDecimal cheapest = amounts.get(0);
        BigDecimal dearest = amounts.get(amounts.size() - 1);
        if (dearest.compareTo(cheapest.multiply(MAX_AMOUNT_SPREAD)) > 0) {
            return Optional.empty();
        }
        BigDecimal typical = amounts.get(amounts.size() / 2).setScale(2, RoundingMode.HALF_UP);

        LocalDate lastCharged = sorted.get(sorted.size() - 1).getTransactionDate();
        // If the next charge is well overdue it has probably been cancelled.
        if (recurrence.occurrence(lastCharged, 1).isBefore(today.minusDays(STALE_AFTER_DAYS))) {
            return Optional.empty();
        }
        LocalDate reference = today.minusDays(1).isAfter(lastCharged) ? today.minusDays(1) : lastCharged;
        LocalDate nextExpected = recurrence.firstAfter(lastCharged, reference);

        return Optional.of(new SubscriptionSuggestion(
                key, displayName(sorted), typical, recurrence, lastCharged, nextExpected,
                sorted.size(), sorted.size() >= 3 ? "HIGH" : "MEDIUM"));
    }

    /** First meaningful word of a merchant name, lower-case letters only. Null if there isn't one. */
    static String merchantKey(String name) {
        if (name == null) return null;
        return Arrays.stream(DOMAIN_SUFFIX.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("").split("[^a-z]+"))
                .filter(token -> token.length() >= 3 && !NOISE.contains(token))
                .findFirst()
                .orElse(null);
    }

    /**
     * A readable name from the most recent charge: domain suffixes, company suffixes and reference numbers
     * dropped. Shouting names ("DISNEY PLUS") become title case, but names that already have their own
     * capitalisation ("PureGym", "Spotify AB") are left as the merchant wrote them.
     */
    static String displayName(List<Transaction> chronological) {
        String raw = chronological.get(chronological.size() - 1).getName();
        List<String> words = new ArrayList<>();
        for (String word : DOMAIN_SUFFIX.matcher(raw).replaceAll("").trim().split("\\s+")) {
            if (word.matches(".*\\d.*")) break; // everything from the first reference number on is noise
            if (word.isBlank() || COMPANY_SUFFIXES.contains(word.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", ""))) continue;
            boolean shouting = word.equals(word.toUpperCase(Locale.ROOT)) && word.length() > 3;
            words.add(shouting ? Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT) : word);
        }
        String name = String.join(" ", words).trim();
        return name.isEmpty() ? raw.trim() : name;
    }

    private String requireValidKey(String key) {
        if (key == null || !KEY_FORMAT.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid suggestion");
        }
        return key;
    }
}
