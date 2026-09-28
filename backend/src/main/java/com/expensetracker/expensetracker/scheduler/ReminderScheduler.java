package com.expensetracker.expensetracker.scheduler;

import com.expensetracker.expensetracker.service.BudgetAlertService;
import com.expensetracker.expensetracker.service.GoalService;
import com.expensetracker.expensetracker.service.MonthlySummaryService;
import com.expensetracker.expensetracker.service.ReminderService;
import com.expensetracker.expensetracker.service.delivery.ReminderDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the reminder job (calendar reminders, budget alerts, regular savings and monthly summaries) hourly and once at startup.
 * Running often is safe because each alert is only ever created once, and it means a host that
 * sleeps overnight still catches up as soon as it wakes.
 *
 * Settings (all optional):
 *   app.reminders.enabled  turn the job off (default true)
 *   app.reminders.cron     when to run (default: top of every hour)
 *   app.reminders.zone     time zone used for "today" (default Europe/London)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private final ReminderService reminderService;
    private final BudgetAlertService budgetAlertService;
    private final GoalService goalService;
    private final MonthlySummaryService summaryService;
    private final ReminderDeliveryService deliveryService;

    @Scheduled(cron = "${app.reminders.cron:0 0 * * * *}", zone = "${app.reminders.zone:Europe/London}")
    public void scheduledRun() {
        run();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        run();
    }

    private void run() {
        try {
            int rolled = reminderService.rollSubscriptionsForward();
            int created = reminderService.generateReminders();
            int budgetAlerts = budgetAlertService.checkAll();
            int savings = goalService.processPlans();
            int summaries = summaryService.sendMonthlySummaries();
            int delivered = deliveryService.deliverPending();
            log.info("Reminder job finished: {} subscription(s) rolled forward, {} reminder(s) and {} budget alert(s) created, "
                    + "{} regular saving(s), {} monthly summar(ies), {} sent by email or push",
                    rolled, created, budgetAlerts, savings, summaries, delivered);
        } catch (RuntimeException ex) {
            // Never let a failed run kill the scheduler thread; the next run tries again.
            log.error("Reminder job failed", ex);
        }
    }
}
