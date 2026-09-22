package com.expensetracker.expensetracker.scheduler;

import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.service.BudgetAlertService;
import com.expensetracker.expensetracker.service.TransactionService;
import com.expensetracker.expensetracker.service.delivery.ReminderDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Syncs everyone's banks in the background, so money in / money out alerts and budget
 * warnings reach the user's phone without them opening the app first.
 *
 * Settings (all optional):
 *   app.sync.enabled  turn background syncing off (default true)
 *   app.sync.cron     when to run (default: every 4 hours, on the hour)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.sync.enabled", havingValue = "true", matchIfMissing = true)
public class SyncScheduler {

    private final BankAccountRepository bankAccountRepository;
    private final TransactionService transactionService;
    private final BudgetAlertService budgetAlertService;
    private final ReminderDeliveryService deliveryService;

    @Scheduled(cron = "${app.sync.cron:0 0 */4 * * *}", zone = "${app.reminders.zone:Europe/London}")
    public void run() {
        int users = 0;
        int saved = 0;
        for (User user : bankAccountRepository.findUsersWithAccounts()) {
            // One bank being down must not stop everyone else's sync.
            try {
                saved += transactionService.syncTransactions(user, false);
                users++;
            } catch (RuntimeException ex) {
                log.warn("Background sync failed for user {}: {}", user.getId(), ex.getMessage());
            }
        }
        try {
            int budgetAlerts = budgetAlertService.checkAll();
            int delivered = deliveryService.deliverPending();
            log.info("Background sync finished: {} user(s), {} new transaction(s), {} budget alert(s), {} sent by email or push",
                    users, saved, budgetAlerts, delivered);
        } catch (RuntimeException ex) {
            log.error("Background sync could not send alerts", ex);
        }
    }
}
