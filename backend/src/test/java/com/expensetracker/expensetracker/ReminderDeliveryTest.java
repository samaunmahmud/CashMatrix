package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.*;
import com.expensetracker.expensetracker.repository.*;
import com.expensetracker.expensetracker.service.ReminderService;
import com.expensetracker.expensetracker.service.delivery.PushGateway;
import com.expensetracker.expensetracker.service.delivery.ReminderDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** "Now" is 2026-09-20 09:00 UTC. Email and push senders are mocked; each test uses its own user. */
@SpringBootTest
@Import(FixedClockTestConfig.class)
class ReminderDeliveryTest {

    @Autowired ReminderDeliveryService delivery;
    @Autowired ReminderService reminders;
    @Autowired UserRepository userRepository;
    @Autowired CalendarEventRepository eventRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationPreferenceRepository preferenceRepository;
    @Autowired PushSubscriptionRepository pushRepository;
    @Autowired Clock clock;
    @MockBean JavaMailSender mailSender;
    @MockBean PushGateway pushGateway;

    User user;

    @BeforeEach
    void newUser() {
        // Delivery looks at everyone's recent reminders, so start each test from a clean slate.
        notificationRepository.deleteAll();
        pushRepository.deleteAll();
        preferenceRepository.deleteAll();

        user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("x");
        user.setFullName("Samaun Mahmud");
        userRepository.save(user);
        when(pushGateway.isConfigured()).thenReturn(true);
    }

    // --- email ---------------------------------------------------------------

    @Test
    void emailsAReminderOnceWhenTheUserHasOptedIn() {
        enableEmail(true);
        Notification n = reminder("Netflix due tomorrow", "Your subscription \"Netflix\" (9.99) due tomorrow (2026-09-21).", "2026-09-21", 1);

        delivery.deliverPending();
        delivery.deliverPending(); // the next hourly run

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(sent.capture());
        SimpleMailMessage mail = sent.getValue();
        assertThat(mail.getTo()).containsExactly(user.getEmail());
        assertThat(mail.getSubject()).isEqualTo("Netflix due tomorrow");
        assertThat(mail.getText())
                .startsWith("Hi Samaun,")
                .contains("Your subscription \"Netflix\" (9.99) due tomorrow")
                .contains("http://localhost:5173/calendar?date=2026-09-21")
                .contains("turn them off any time in Settings");
        assertThat(notificationRepository.findById(n.getId()).orElseThrow().isEmailSent()).isTrue();
    }

    @Test
    void neverEmailsSomeoneWhoHasNotOptedIn() {
        reminder("Rent due tomorrow", "msg", "2026-09-21", 1);          // no preference saved at all
        enableEmail(false);
        reminder("Bills due tomorrow", "msg", "2026-09-21", 1);         // explicitly off

        delivery.deliverPending();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void aFailedEmailIsRetriedNextRunAndThenNotRepeated() {
        enableEmail(true);
        reminder("Gym due today", "msg", "2026-09-20", 0);
        doThrow(new MailSendException("smtp down")).doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        int firstRun = delivery.deliverPending();
        int secondRun = delivery.deliverPending();
        int thirdRun = delivery.deliverPending();

        assertThat(firstRun).as("failed").isZero();
        assertThat(secondRun).as("retry succeeded").isEqualTo(1);
        assertThat(thirdRun).as("not sent again").isZero();
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    // --- push ----------------------------------------------------------------

    @Test
    void pushesToEveryDeviceAndForgetsOnesThatHaveGone() {
        PushSubscription phone = device("https://fcm.googleapis.com/fcm/send/phone");
        PushSubscription oldLaptop = device("https://fcm.googleapis.com/fcm/send/old-laptop");
        Notification n = reminder("Council tax due in 3 days", "msg", "2026-09-23", 1);
        when(pushGateway.send(argThatEndpoint(phone), anyString())).thenReturn(PushGateway.Result.SENT);
        when(pushGateway.send(argThatEndpoint(oldLaptop), anyString())).thenReturn(PushGateway.Result.GONE);

        int sent = delivery.deliverPending();

        assertThat(sent).isEqualTo(1);
        assertThat(pushRepository.findByUser(user)).extracting(PushSubscription::getEndpoint)
                .containsExactly(phone.getEndpoint());
        assertThat(notificationRepository.findById(n.getId()).orElseThrow().isPushSent()).isTrue();

        delivery.deliverPending();
        verify(pushGateway, times(1)).send(argThatEndpoint(phone), anyString());
    }

    @Test
    void thePushPayloadCarriesTitleTextAndWhereToGo() {
        device("https://fcm.googleapis.com/fcm/send/phone");
        reminder("Netflix due tomorrow", "Your subscription is due.", "2026-09-21", 1);
        when(pushGateway.send(any(), anyString())).thenReturn(PushGateway.Result.SENT);

        delivery.deliverPending();

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(pushGateway).send(any(), payload.capture());
        assertThat(payload.getValue())
                .contains("\"title\":\"Netflix due tomorrow\"")
                .contains("\"body\":\"Your subscription is due.\"")
                .contains("\"url\":\"/calendar?date=2026-09-21\"");
    }

    @Test
    void aFailedPushIsRetriedNextRun() {
        device("https://fcm.googleapis.com/fcm/send/phone");
        reminder("Gym due today", "msg", "2026-09-20", 0);
        when(pushGateway.send(any(), anyString())).thenReturn(PushGateway.Result.FAILED, PushGateway.Result.SENT);

        assertThat(delivery.deliverPending()).isZero();
        assertThat(delivery.deliverPending()).isEqualTo(1);
        assertThat(delivery.deliverPending()).isZero();
        verify(pushGateway, times(2)).send(any(), anyString());
    }

    @Test
    void doesNothingForPushWhenNoDeviceIsRegisteredOrPushIsNotConfigured() {
        reminder("No device", "msg", "2026-09-21", 1);
        delivery.deliverPending();
        verify(pushGateway, never()).send(any(), anyString());

        device("https://fcm.googleapis.com/fcm/send/phone");
        when(pushGateway.isConfigured()).thenReturn(false);
        delivery.deliverPending();
        verify(pushGateway, never()).send(any(), anyString());
    }

    // --- timing ----------------------------------------------------------------

    @Test
    void ignoresReminderOlderThanADayInsteadOfFloodingSomeoneWhoJustSwitchedItOn() {
        enableEmail(true);
        reminder("Old news", "msg", "2026-09-18", 1, Duration.ofHours(25));

        delivery.deliverPending();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void remindersRaisedWhileTheUserIsInTheAppAreNotAlsoSentToThem() {
        enableEmail(true);
        device("https://fcm.googleapis.com/fcm/send/phone");
        CalendarEvent event = event("Netflix", "2026-09-21", 2);

        int created = reminders.generateReminders(user);      // what happens when they open their alerts
        delivery.deliverPending();

        assertThat(created).isEqualTo(1);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(pushGateway, never()).send(any(), anyString());
        assertThat(event.getId()).isNotNull();
    }

    @Test
    void remindersRaisedByTheBackgroundJobAreSent() {
        enableEmail(true);
        event("Netflix", "2026-09-21", 2);

        reminders.generateReminders();   // the hourly job, for everyone
        delivery.deliverPending();

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(sent.capture());
        assertThat(sent.getAllValues()).anyMatch(m -> m.getTo() != null && List.of(m.getTo()).contains(user.getEmail()));
    }

    // --- helpers -------------------------------------------------------------

    private void enableEmail(boolean enabled) {
        NotificationPreference preference = preferenceRepository.findByUser(user).orElseGet(() -> {
            NotificationPreference p = new NotificationPreference();
            p.setUser(user);
            return p;
        });
        preference.setEmailEnabled(enabled);
        preferenceRepository.save(preference);
    }

    private PushSubscription device(String endpoint) {
        PushSubscription subscription = new PushSubscription();
        subscription.setUser(user);
        subscription.setEndpoint(endpoint + "-" + user.getId());
        subscription.setP256dh("key");
        subscription.setAuth("auth");
        return pushRepository.save(subscription);
    }

    private PushSubscription argThatEndpoint(PushSubscription expected) {
        return argThat(s -> s != null && s.getEndpoint().equals(expected.getEndpoint()));
    }

    private CalendarEvent event(String title, String date, int remindDays) {
        CalendarEvent event = new CalendarEvent();
        event.setUser(user);
        event.setTitle(title);
        event.setType(EventType.SUBSCRIPTION);
        event.setRecurrence(Recurrence.MONTHLY);
        event.setStartDate(LocalDate.parse(date));
        event.setNextDueDate(LocalDate.parse(date));
        event.setRemindDaysBefore(remindDays);
        return eventRepository.save(event);
    }

    private Notification reminder(String title, String message, String due, int remindDays) {
        return reminder(title, message, due, remindDays, Duration.ZERO);
    }

    private Notification reminder(String title, String message, String due, int remindDays, Duration age) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setEvent(event(title, due, remindDays));
        notification.setDueDate(LocalDate.parse(due));
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCreatedAt(clock.instant().minus(age));
        return notificationRepository.save(notification);
    }
}
