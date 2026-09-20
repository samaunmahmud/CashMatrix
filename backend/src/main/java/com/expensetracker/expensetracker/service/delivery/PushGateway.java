package com.expensetracker.expensetracker.service.delivery;

import com.expensetracker.expensetracker.model.PushSubscription;

/** Sends a message to one subscribed browser. An interface so tests can stand in for the real push services. */
public interface PushGateway {

    enum Result {
        SENT,
        /** The device unsubscribed or the subscription expired; it should be forgotten. */
        GONE,
        /** A temporary problem; worth trying again later. */
        FAILED
    }

    /** False until VAPID keys are configured. */
    boolean isConfigured();

    /** The public key browsers need in order to subscribe. Null when not configured. */
    String publicKey();

    Result send(PushSubscription subscription, String jsonPayload);
}
