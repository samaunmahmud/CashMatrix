package com.expensetracker.expensetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * An in-app reminder for an upcoming (or just missed) calendar item.
 *
 * The unique constraint on (event, due date) is what guarantees a user is
 * reminded only once per occurrence, however many times the reminder job runs.
 */
@Entity
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "due_date"})
)
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private CalendarEvent event;

    // The occurrence this reminder is about, not the day it was created.
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    // "read" is a reserved word in some databases, so the column gets its own name.
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    // Whether the reminder has already gone out by email / phone push, so a retry never repeats it.
    // The column definitions carry a default so the columns can be added to a table that already has rows.
    @Column(name = "email_sent", nullable = false, columnDefinition = "boolean default false")
    private boolean emailSent = false;

    @Column(name = "push_sent", nullable = false, columnDefinition = "boolean default false")
    private boolean pushSent = false;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
