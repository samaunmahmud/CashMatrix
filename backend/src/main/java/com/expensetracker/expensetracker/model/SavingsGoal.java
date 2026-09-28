package com.expensetracker.expensetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Something the user is saving towards, such as "Holiday: 1,200 by June".
 *
 * The app can't move money, so the saved amount is what the user tells it they have put aside;
 * each change is kept as a {@link GoalContribution}.
 */
@Entity
@Table(name = "savings_goals")
@Getter
@Setter
@NoArgsConstructor
public class SavingsGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 60)
    private String name;

    // A single emoji to show beside the goal, such as a plane for a holiday. Optional.
    @Column(length = 16)
    private String emoji;

    @Column(name = "target_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "saved_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal savedAmount = BigDecimal.ZERO;

    // When the user wants to have it by. Optional.
    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
