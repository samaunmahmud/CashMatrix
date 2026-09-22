package com.expensetracker.expensetracker.service.delivery;

import com.expensetracker.expensetracker.dto.NotificationSettingsRequest;
import com.expensetracker.expensetracker.dto.NotificationSettingsResponse;
import com.expensetracker.expensetracker.dto.PushSubscribeRequest;
import com.expensetracker.expensetracker.model.NotificationPreference;
import com.expensetracker.expensetracker.model.PushSubscription;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.NotificationPreferenceRepository;
import com.expensetracker.expensetracker.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    // The server posts to whatever address a subscription names, so only the real browser
    // push services are accepted. Without this a user could aim the server at internal addresses.
    static final List<String> PUSH_SERVICE_DOMAINS = List.of(
            "googleapis.com",      // Chrome, Edge, Android
            "mozilla.com",         // Firefox
            "push.apple.com",      // Safari, iOS
            "windows.com");        // Edge on Windows (WNS)

    private final NotificationPreferenceRepository preferenceRepository;
    private final PushSubscriptionRepository pushRepository;
    private final EmailSender emailSender;
    private final PushGateway pushGateway;
    private final ReminderDeliveryService deliveryService;

    @Transactional(readOnly = true)
    public NotificationSettingsResponse get(User user) {
        return describe(user, preferenceRepository.findByUser(user).orElseGet(NotificationPreference::new));
    }

    @Transactional
    public NotificationSettingsResponse update(User user, NotificationSettingsRequest request) {
        if (request.isEmpty()) {
            throw new IllegalArgumentException("Nothing to change");
        }
        NotificationPreference preference = preferenceRepository.findByUser(user).orElseGet(() -> {
            NotificationPreference created = new NotificationPreference();
            created.setUser(user);
            return created;
        });
        if (request.getEmailEnabled() != null) preference.setEmailEnabled(request.getEmailEnabled());
        if (request.getTransactionAlertsEnabled() != null) preference.setTransactionAlertsEnabled(request.getTransactionAlertsEnabled());
        if (request.getTransactionAlertMinimum() != null) preference.setTransactionAlertMinimum(request.getTransactionAlertMinimum());
        preferenceRepository.save(preference);
        return describe(user, preference);
    }

    @Transactional
    public void subscribe(User user, PushSubscribeRequest request) {
        String endpoint = request.getEndpoint().trim();
        requireKnownPushService(endpoint);

        // The same browser can be re-registered (or handed to a different account), so match on the endpoint.
        PushSubscription subscription = pushRepository.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        subscription.setUser(user);
        subscription.setEndpoint(endpoint);
        subscription.setP256dh(request.getKeys().getP256dh());
        subscription.setAuth(request.getKeys().getAuth());
        pushRepository.save(subscription);
    }

    @Transactional
    public void unsubscribe(User user, String endpoint) {
        pushRepository.deleteByUserAndEndpoint(user, endpoint.trim());
    }

    /** Sends a test through each channel that is on, so the user can check it really reaches them. */
    @Transactional
    public Map<String, String> sendTest(User user) {
        String title = "CashMatrix test reminder";
        String message = "If you can read this, reminders are working.";

        String email;
        if (!emailSender.isConfigured()) {
            email = "UNAVAILABLE";
        } else if (!preferenceRepository.findByUser(user).map(NotificationPreference::isEmailEnabled).orElse(false)) {
            email = "OFF";
        } else {
            try {
                emailSender.send(user.getEmail(), title, deliveryService.emailBody(user, message, "/notifications"));
                email = "SENT";
            } catch (RuntimeException ex) {
                email = "FAILED";
            }
        }

        String push;
        if (!pushGateway.isConfigured()) {
            push = "UNAVAILABLE";
        } else {
            List<PushSubscription> devices = pushRepository.findByUser(user);
            if (devices.isEmpty()) {
                push = "NO_DEVICES";
            } else {
                String payload = deliveryService.pushPayload(title, message, "/notifications");
                boolean anySent = false;
                for (PushSubscription device : devices) {
                    switch (pushGateway.send(device, payload)) {
                        case SENT -> anySent = true;
                        case GONE -> pushRepository.delete(device);
                        case FAILED -> { }
                    }
                }
                push = anySent ? "SENT" : "FAILED";
            }
        }
        return Map.of("email", email, "push", push);
    }

    private NotificationSettingsResponse describe(User user, NotificationPreference preference) {
        return new NotificationSettingsResponse(
                preference.isEmailEnabled(),
                emailSender.isConfigured(),
                pushGateway.isConfigured(),
                pushGateway.publicKey(),
                pushRepository.countByUser(user),
                preference.isTransactionAlertsEnabled(),
                preference.getTransactionAlertMinimum());
    }

    private void requireKnownPushService(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("That isn't a valid push address");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean known = "https".equalsIgnoreCase(uri.getScheme())
                && PUSH_SERVICE_DOMAINS.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
        if (!known) {
            throw new IllegalArgumentException("That isn't a supported push service");
        }
    }
}
