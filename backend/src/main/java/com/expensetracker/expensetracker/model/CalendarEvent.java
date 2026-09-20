package com.expensetracker.expensetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One item on a user's calendar: a task, a one-off payment, or a subscription.
 *
 * A repeating item is stored once. startDate is its anchor (the first
 * occurrence) and nextDueDate is the occurrence currently waiting to be
 * dealt with. Marking it done moves nextDueDate forward one occurrence.
 */
@Entity
@Table(name = "calendar_events")
@Getter
@Setter
@NoArgsConstructor
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;

    // Left empty for tasks. Currency is not stored, the UI decides how to show it.
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Recurrence recurrence = Recurrence.NONE;

    // How many days before the due date the user wants to be reminded (0 = on the day).
    @Column(name = "remind_days_before", nullable = false)
    private int remindDaysBefore = 1;

    // Only used for one-off items. Repeating items advance nextDueDate instead.
    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    /** False once a one-off item is done. Repeating items never stop being active. */
    public boolean isActive() {
        return recurrence.isRecurring() || !completed;
    }
}
