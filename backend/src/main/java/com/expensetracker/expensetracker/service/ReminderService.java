package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.model.CalendarEvent;
import com.expensetracker.expensetracker.model.EventType;
import com.expensetracker.expensetracker.model.Notification;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.CalendarEventRepository;
import com.expensetracker.expensetracker.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Turns upcoming calendar items into notifications.
 *
 * A reminder is created once the due date is within the item's "remind me N days
 * before" window, and again is never created for the same occurrence (the
 * database enforces that). Items that were missed keep producing a reminder for
 * a short grace period so a server that was asleep on the due date still tells
 * the user, just later.
 */
@Service
@RequiredArgsConstructor
public class ReminderService {

    static final int OVERDUE_GRACE_DAYS = 7;
    static final int MAX_REMIND_DAYS = 30;

    private final CalendarEventRepository eventRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    /** Reminders for every user. Called by the scheduler. */
    @Transactional
    public int generateReminders() {
        LocalDate today = LocalDate.now(clock);
        return createDueReminders(eventRepository.findByNextDueDateBetween(
                today.minusDays(OVERDUE_GRACE_DAYS), today.plusDays(MAX_REMIND_DAYS)), today);
    }

    /** Reminders for one user. Called when they open their notifications. */
    @Transactional
    public int generateReminders(User user) {
        LocalDate today = LocalDate.now(clock);
        return createDueReminders(eventRepository.findByUserAndNextDueDateBetween(
                user, today.minusDays(OVERDUE_GRACE_DAYS), today.plusDays(MAX_REMIND_DAYS)), today);
    }

    /**
     * Subscriptions are charged automatically, so once the day has passed they simply
     * move on to their next occurrence. Bills and tasks wait for the user to confirm.
     */
    @Transactional
    public int rollSubscriptionsForward() {
        LocalDate today = LocalDate.now(clock);
        int rolled = 0;
        for (CalendarEvent event : eventRepository.findByTypeAndNextDueDateBefore(EventType.SUBSCRIPTION, today)) {
            if (event.getRecurrence().isRecurring()) {
                event.setNextDueDate(event.getRecurrence().firstOnOrAfter(event.getStartDate(), today));
                rolled++;
            }
        }
        return rolled;
    }

    private int createDueReminders(List<CalendarEvent> candidates, LocalDate today) {
        int created = 0;
        for (CalendarEvent event : candidates) {
            if (!event.isActive()) {
                continue;
            }
            LocalDate due = event.getNextDueDate();
            if (today.isBefore(due.minusDays(event.getRemindDaysBefore()))) {
                continue; // too early to bother them
            }
            if (notificationRepository.existsByEventAndDueDate(event, due)) {
                continue; // already reminded for this occurrence
            }
            notificationRepository.save(buildNotification(event, due, today));
            created++;
        }
        return created;
    }

    private Notification buildNotification(CalendarEvent event, LocalDate due, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, due);
        String when;
        if (days == 0) {
            when = "due today";
        } else if (days == 1) {
            when = "due tomorrow";
        } else if (days > 1) {
            when = "due in " + days + " days";
        } else if (days == -1) {
            when = "was due yesterday";
        } else {
            when = "was due " + (-days) + " days ago";
        }

        String kind = event.getType().name().toLowerCase();
        String amount = event.getAmount() != null ? " (" + event.getAmount().toPlainString() + ")" : "";

        Notification notification = new Notification();
        notification.setUser(event.getUser());
        notification.setEvent(event);
        notification.setDueDate(due);
        notification.setTitle(event.getTitle() + " " + when);
        notification.setMessage("Your " + kind + " \"" + event.getTitle() + "\"" + amount
                + " " + when + " (" + due + ").");
        return notification;
    }
}
