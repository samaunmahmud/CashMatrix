package com.expensetracker.expensetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** How a user wants to be reminded, beyond the in-app alerts everyone gets. */
@Entity
@Table(name = "notification_preferences", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Off until the user opts in: nobody should get emails they never asked for.
    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = false;
}
