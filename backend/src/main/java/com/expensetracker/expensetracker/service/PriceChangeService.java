package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.model.*;
import com.expensetracker.expensetracker.repository.CalendarEventRepository;
import com.expensetracker.expensetracker.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * "Netflix has gone up to £12.99": an alert when a regular payment on the calendar is charged at a
 * different price, which also puts the new price on the calendar so reminders and the forecast use it.
 *
 * Small wobbles (under 50p, or under 2%) are ignored, since some bills round differently month to month.
 */
@Service
@RequiredArgsConstructor
public class PriceChangeService {

    static final BigDecimal MIN_CHANGE = new BigDecimal("0.50");
    static final BigDecimal MIN_CHANGE_SHARE = new BigDecimal("0.02");
    // A charge that only turns up weeks later still says what the price is now, up to about a month.
    static final int RECENT_DAYS = 35;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK);

    private final CalendarEventRepository eventRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    /**
     * @param newTransactions transactions a sync has just saved, none of them from an account's first import
     * @param userIsPresent   true when the user started the sync and will see the alerts in the app
     * @return how many price changes were found
     */
    @Transactional
    public int check(User user, Collection<Transaction> newTransactions, boolean userIsPresent) {
        List<CalendarEvent> bills = eventRepository.findByUserOrderByNextDueDateAsc(user).stream()
                .filter(e -> e.getType() == EventType.SUBSCRIPTION || e.getType() == EventType.PAYMENT)
                .filter(e -> e.getRecurrence().isRecurring())
                .filter(e -> e.getAmount() != null && e.getAmount().signum() > 0)
                .toList();
        if (bills.isEmpty() || newTransactions.isEmpty()) return 0;

        LocalDate since = LocalDate.now(clock).minusDays(RECENT_DAYS);
        List<Transaction> charges = newTransactions.stream()
                .filter(tx -> tx.getAmount().signum() > 0 && !Boolean.TRUE.equals(tx.getPending()))
                .filter(tx -> !tx.getTransactionDate().isBefore(since))
                .sorted(Comparator.comparing(Transaction::getTransactionDate))  // the latest price wins
                .toList();

        int found = 0;
        for (Transaction charge : charges) {
            for (CalendarEvent bill : bills) {
                if (!BillMatcher.sharesWord(BillMatcher.words(bill.getTitle()), charge.getName())) continue;
                if (!isRealChange(bill.getAmount(), charge.getAmount())) break;

                String key = "price-" + bill.getId() + "-" + charge.getId();
                if (!notificationRepository.existsByAlertKey(key)) {
                    Notification notification = build(user, bill, charge, key);
                    if (userIsPresent) {
                        notification.setEmailSent(true);
                        notification.setPushSent(true);
                    }
                    notificationRepository.save(notification);
                    found++;
                }
                bill.setAmount(charge.getAmount());
                eventRepository.save(bill);
                break;
            }
        }
        return found;
    }

    static boolean isRealChange(BigDecimal was, BigDecimal now) {
        BigDecimal change = now.subtract(was).abs();
        return change.compareTo(MIN_CHANGE) >= 0 && change.compareTo(was.multiply(MIN_CHANGE_SHARE)) >= 0;
    }

    private static Notification build(User user, CalendarEvent bill, Transaction charge, String key) {
        String currency = charge.getBankAccount().getCurrency();
        boolean up = charge.getAmount().compareTo(bill.getAmount()) > 0;
        String now = Money.format(charge.getAmount(), currency);

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setAlertKey(key);
        notification.setLink("/calendar?date=" + bill.getNextDueDate());
        notification.setDueDate(charge.getTransactionDate());
        notification.setTitle(bill.getTitle() + (up ? " has gone up to " : " has gone down to ") + now);
        notification.setMessage("It was " + Money.format(bill.getAmount(), currency) + ". Charged "
                + now + " on " + charge.getTransactionDate().format(DAY)
                + ". Your calendar now shows the new price.");
        return notification;
    }
}
