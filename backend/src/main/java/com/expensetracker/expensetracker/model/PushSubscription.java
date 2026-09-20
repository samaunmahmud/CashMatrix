package com.expensetracker.expensetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One browser or phone that agreed to receive push notifications for a user. */
@Entity
@Table(name = "push_subscriptions", uniqueConstraints = @UniqueConstraint(columnNames = "endpoint"))
@Getter
@Setter
@NoArgsConstructor
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The push service's address for this device (unique per browser install).
    @Column(nullable = false, length = 1000)
    private String endpoint;

    // Keys the browser gave us so only it can read what we send.
    @Column(nullable = false, length = 200)
    private String p256dh;

    @Column(nullable = false, length = 100)
    private String auth;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
