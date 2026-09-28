package com.expensetracker.expensetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Money put towards a savings goal, or taken back out of it (a negative amount). */
@Entity
@Table(name = "goal_contributions")
@Getter
@Setter
@NoArgsConstructor
public class GoalContribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id", nullable = false)
    private SavingsGoal goal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "made_on", nullable = false)
    private LocalDate madeOn;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
