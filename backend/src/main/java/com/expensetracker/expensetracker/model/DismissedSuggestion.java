package com.expensetracker.expensetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A detected subscription the user said they don't want to be suggested again. */
@Entity
@Table(
        name = "dismissed_suggestions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "merchant_key"})
)
@Getter
@Setter
@NoArgsConstructor
public class DismissedSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "merchant_key", nullable = false, length = 60)
    private String merchantKey;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
