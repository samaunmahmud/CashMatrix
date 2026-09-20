package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.*;
import com.expensetracker.expensetracker.repository.CalendarEventRepository;
import com.expensetracker.expensetracker.repository.NotificationRepository;
import com.expensetracker.expensetracker.repository.UserRepository;
import com.expensetracker.expensetracker.service.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the reminder job directly. "Today" is fixed at 2026-09-20. */
@SpringBootTest
@Import(FixedClockTestConfig.class)
class ReminderServiceTest {

    @Autowired ReminderService reminderService;
    @Autowired CalendarEventRepository eventRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;

    User user;

    @BeforeEach
    void createUser() {
        user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("x");
        user.setFullName("Reminder Test");
        userRepository.save(user);
    }

    @Test
    void runningTheJobTwiceCreatesOneReminder() {
        save("Rent", EventType.PAYMENT, Recurrence.MONTHLY, "2026-09-21", 2);

        int first = reminderService.generateReminders(user);
        int second = reminderService.generateReminders(user);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(user)).hasSize(1);
    }

    @Test
    void remindsAboutAnItemThatWasMissedRecently() {
        save("Missed bill", EventType.PAYMENT, Recurrence.NONE, "2026-09-18", 1); // 2 days ago

        reminderService.generateReminders(user);

        assertThat(notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(user))
                .extracting(Notification::getTitle)
                .containsExactly("Missed bill was due 2 days ago");
    }

    @Test
    void stopsRemindingOnceAnItemIsTooOverdue() {
        save("Ancient bill", EventType.PAYMENT, Recurrence.NONE, "2026-09-05", 1); // 15 days ago

        reminderService.generateReminders(user);

        assertThat(notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(user)).isEmpty();
    }

    @Test
    void doesNotRemindAboutACompletedOneOffItem() {
        CalendarEvent done = save("Done", EventType.TASK, Recurrence.NONE, "2026-09-21", 2);
        done.setCompleted(true);
        eventRepository.save(done);

        reminderService.generateReminders(user);

        assertThat(notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(user)).isEmpty();
    }

    @Test
    void wordsTodayAndTomorrowNaturally() {
        save("Today thing", EventType.TASK, Recurrence.NONE, "2026-09-20", 0);
        save("Tomorrow thing", EventType.TASK, Recurrence.NONE, "2026-09-21", 1);

        reminderService.generateReminders(user);

        assertThat(notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(user))
                .extracting(Notification::getTitle)
                .containsExactlyInAnyOrder("Today thing due today", "Tomorrow thing due tomorrow");
    }

    @Test
    void subscriptionsRollForwardAfterTheirDueDateButBillsDoNot() {
        CalendarEvent sub = save("Spotify", EventType.SUBSCRIPTION, Recurrence.MONTHLY, "2026-08-19", 1);
        sub.setNextDueDate(LocalDate.of(2026, 9, 19)); // yesterday
        eventRepository.save(sub);
        CalendarEvent bill = save("Electric", EventType.PAYMENT, Recurrence.MONTHLY, "2026-08-19", 1);
        bill.setNextDueDate(LocalDate.of(2026, 9, 19));
        eventRepository.save(bill);

        reminderService.rollSubscriptionsForward();

        assertThat(eventRepository.findById(sub.getId()).orElseThrow().getNextDueDate())
                .isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(eventRepository.findById(bill.getId()).orElseThrow().getNextDueDate())
                .as("bills wait for the user to mark them paid")
                .isEqualTo(LocalDate.of(2026, 9, 19));
    }

    private CalendarEvent save(String title, EventType type, Recurrence recurrence, String date, int remindDays) {
        CalendarEvent event = new CalendarEvent();
        event.setUser(user);
        event.setTitle(title);
        event.setType(type);
        event.setRecurrence(recurrence);
        event.setStartDate(LocalDate.parse(date));
        event.setNextDueDate(LocalDate.parse(date));
        event.setRemindDaysBefore(remindDays);
        return eventRepository.save(event);
    }
}
