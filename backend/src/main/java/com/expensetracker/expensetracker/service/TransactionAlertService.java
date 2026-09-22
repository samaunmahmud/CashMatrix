package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.Notification;
import com.expensetracker.expensetracker.model.NotificationPreference;
import com.expensetracker.expensetracker.model.Transaction;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.NotificationPreferenceRepository;
import com.expensetracker.expensetracker.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;

/**
 * "£45.00 spent at Tesco": an alert for each new transaction over the user's chosen amount,
 * like the money in / money out alerts of a bank app.
 *
 * Only settled, recent transactions count. Pending ones are skipped because the bank re-sends
 * them under a new id once they settle, which would alert twice, and a transaction from weeks
 * ago that only now turned up isn't news.
 */
@Service
@RequiredArgsConstructor
public class TransactionAlertService {

    static final int RECENT_DAYS = 3;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK);

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final Clock clock;

    /**
     * @param newTransactions transactions a sync has just saved, none of them from an account's first import
     * @param userIsPresent   true when the user started the sync and will see the alerts in the app
     */
    @Transactional
    public int alert(User user, Collection<Transaction> newTransactions, boolean userIsPresent) {
        if (newTransactions.isEmpty()) return 0;

        NotificationPreference preference = preferenceRepository.findByUser(user).orElse(null);
        boolean enabled = preference == null || preference.isTransactionAlertsEnabled();
        if (!enabled) return 0;
        BigDecimal minimum = preference != null ? preference.getTransactionAlertMinimum() : NotificationPreference.DEFAULT_ALERT_MINIMUM;

        LocalDate since = LocalDate.now(clock).minusDays(RECENT_DAYS);
        int created = 0;
        for (Transaction tx : newTransactions) {
            if (Boolean.TRUE.equals(tx.getPending())) continue;
            if (tx.getTransactionDate().isBefore(since)) continue;
            if (tx.getAmount().abs().compareTo(minimum) < 0) continue;

            String key = "tx-" + tx.getId();
            if (notificationRepository.existsByAlertKey(key)) continue;

            Notification notification = build(user, tx, key);
            if (userIsPresent) {
                notification.setEmailSent(true);
                notification.setPushSent(true);
            }
            notificationRepository.save(notification);
            created++;
        }
        return created;
    }

    private static Notification build(User user, Transaction tx, String key) {
        BankAccount account = tx.getBankAccount();
        String merchant = MerchantNames.display(tx.getName());
        boolean moneyOut = tx.getAmount().signum() > 0;
        String amount = Money.format(tx.getAmount().abs(), account.getCurrency());

        String accountName = account.getName() != null ? account.getName() : "your account";
        if (account.getMask() != null) accountName += " ending " + account.getMask();

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setAlertKey(key);
        notification.setLink("/accounts/" + account.getId());
        notification.setDueDate(tx.getTransactionDate());
        notification.setTitle(moneyOut ? amount + " spent at " + merchant : amount + " received from " + merchant);
        notification.setMessage((moneyOut ? "Paid from " : "Paid into ") + accountName + " on "
                + tx.getTransactionDate().format(DAY) + ".");
        return notification;
    }
}
