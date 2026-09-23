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
 * same amount on a steady weekly, monthly or yearly rhythm.
 *
 * Weekly and monthly charges are judged on the last few months, so an old price or a
 * cancel-and-rejoin doesn't hide a current subscription. Yearly ones need the up to two years
 * a first import brings in. Merchants are grouped by {@link MerchantNames#key}.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionDetectionService {

    static final int STALE_AFTER_DAYS = 7;
    static final int DEFAULT_REMIND_DAYS = 2;
    // Subscriptions creep up by pennies or a price rise; a wilder spread is a shop, not a subscription.
    static final BigDecimal MAX_AMOUNT_SPREAD = new BigDecimal("1.15");
    // Yearly renewals often go up by more (Prime went from £79 to £95).
    static final BigDecimal MAX_YEARLY_AMOUNT_SPREAD = new BigDecimal("1.25");
    static final int RECENT_MONTHS = 6;

    private static final Pattern KEY_FORMAT = Pattern.compile("[a-z]{3,40}");

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
        List<Transaction> all = transactions.stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDate))
                .toList();
        LocalDate recentFrom = today.minusMonths(RECENT_MONTHS);
        List<Transaction> recent = all.stream()
                .filter(tx -> !tx.getTransactionDate().isBefore(recentFrom))
                .toList();
        return analyse(key, recent, today, false).or(() -> analyse(key, all, today, true));
    }

    private Optional<SubscriptionSuggestion> analyse(String key, List<Transaction> sorted, LocalDate today, boolean yearly) {
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
        BigDecimal maxSpread = MAX_AMOUNT_SPREAD;
        if (yearly) {
            if (median < 355 || median > 375) {
                return Optional.empty();
            }
            recurrence = Recurrence.YEARLY;
            tolerance = 10;
            minimumCharges = 2;
            maxSpread = MAX_YEARLY_AMOUNT_SPREAD;
        } else if (median >= 6 && median <= 8) {
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
        if (dearest.compareTo(cheapest.multiply(maxSpread)) > 0) {
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

    static String merchantKey(String name) {
        return MerchantNames.key(name);
    }

    /** A readable name, taken from the most recent charge. */
    static String displayName(List<Transaction> chronological) {
        return MerchantNames.display(chronological.get(chronological.size() - 1).getName());
    }

    private String requireValidKey(String key) {
        if (key == null || !KEY_FORMAT.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid suggestion");
        }
        return key;
    }
}
