package com.expensetracker.expensetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A passkey: a key pair kept on the user's phone or computer and unlocked with their
 * fingerprint, face or screen lock. The server only ever holds the public half.
 */
@Entity
@Table(name = "passkeys")
@Getter
@Setter
@NoArgsConstructor
public class Passkey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // All base64url. The credential id is what the device sends back when logging in.
    @Column(name = "credential_id", nullable = false, unique = true, length = 1024)
    private String credentialId;

    // A random id for the user, shared by all their passkeys. Kept apart from the database id and
    // email so the device never holds anything that identifies them.
    @Column(name = "user_handle", nullable = false, length = 128)
    private String userHandle;

    @Column(name = "public_key_cose", nullable = false, length = 2048)
    private String publicKeyCose;

    // Goes up each time the passkey is used; a count that goes backwards suggests a cloned key.
    @Column(name = "signature_count", nullable = false)
    private long signatureCount;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
