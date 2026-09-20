package com.expensetracker.expensetracker.service.delivery;

import com.expensetracker.expensetracker.model.Notification;
import com.expensetracker.expensetracker.model.NotificationPreference;
import com.expensetracker.expensetracker.model.PushSubscription;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.NotificationPreferenceRepository;
import com.expensetracker.expensetracker.repository.NotificationRepository;
import com.expensetracker.expensetracker.repository.PushSubscriptionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Sends new reminders out by email and phone push, at most once per channel.
 *
 * Each notification remembers what has gone out, so if a channel fails the next hourly run
 * simply tries again, and a channel that succeeded is never repeated. Only reminders from
 * the last day are considered, so switching a channel on never floods someone with old news.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderDeliveryService {

    static final Duration FRESHNESS = Duration.ofHours(24);

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final PushSubscriptionRepository pushRepository;
    private final EmailSender emailSender;
    private final PushGateway pushGateway;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Value("${app.public-url:http://localhost:5173}")
    private String publicUrl;

    /** @return how many messages went out (an email or a push to one device each counts as one) */
    @Transactional
    public int deliverPending() {
        Instant since = clock.instant().minus(FRESHNESS);
        int sent = 0;
        for (Notification notification : notificationRepository.findUndelivered(since)) {
            User user = notification.getUser();
            if (!notification.isEmailSent()) {
                sent += deliverEmail(notification, user);
            }
            if (!notification.isPushSent()) {
                sent += deliverPush(notification, user);
            }
        }
        return sent;
    }

    private int deliverEmail(Notification notification, User user) {
        if (!emailSender.isConfigured()) return 0;
        boolean wanted = preferenceRepository.findByUser(user).map(NotificationPreference::isEmailEnabled).orElse(false);
        if (!wanted) return 0;

        try {
            emailSender.send(user.getEmail(), notification.getTitle(), emailBody(notification, user));
            notification.setEmailSent(true);
            return 1;
        } catch (RuntimeException ex) {
            log.warn("Could not email reminder {}: {}", notification.getId(), ex.getMessage());
            return 0; // left unsent, so the next run retries
        }
    }

    private int deliverPush(Notification notification, User user) {
        if (!pushGateway.isConfigured()) return 0;
        List<PushSubscription> devices = pushRepository.findByUser(user);
        if (devices.isEmpty()) return 0;

        String payload = pushPayload(notification.getTitle(), notification.getMessage(),
                "/calendar?date=" + notification.getDueDate());
        int delivered = 0;
        for (PushSubscription device : devices) {
            switch (pushGateway.send(device, payload)) {
                case SENT -> delivered++;
                case GONE -> pushRepository.delete(device);
                case FAILED -> { /* try again next run */ }
            }
        }
        if (delivered > 0) {
            notification.setPushSent(true);
        }
        return delivered;
    }

    /** JSON the service worker turns into the notification the user sees. */
    public String pushPayload(String title, String body, String url) {
        try {
            return objectMapper.writeValueAsString(Map.of("title", title, "body", body, "url", url));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public String emailBody(Notification notification, User user) {
        return emailBody(user, notification.getMessage(), "/calendar?date=" + notification.getDueDate());
    }

    public String emailBody(User user, String message, String path) {
        String name = user.getFullName() == null || user.getFullName().isBlank()
                ? "there" : user.getFullName().trim().split("\\s+")[0];
        return "Hi " + name + ",\n\n"
                + message + "\n\n"
                + "Open CashMatrix: " + publicUrl + path + "\n\n"
                + "You're getting this because email reminders are switched on. "
                + "You can turn them off any time in Settings.\n";
    }
}
