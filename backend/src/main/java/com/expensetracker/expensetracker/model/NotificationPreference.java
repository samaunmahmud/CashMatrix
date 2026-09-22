package com.expensetracker.expensetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

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

    // Alerts for money coming in and going out, like a bank app's. On by default, in the app at least;
    // they only reach email or phone if those channels are switched on too.
    // The column definitions carry a default so the columns can be added to a table that already has rows.
    @Column(name = "transaction_alerts_enabled", nullable = false, columnDefinition = "boolean default true")
    private boolean transactionAlertsEnabled = true;

    // Smaller transactions than this don't raise an alert.
    @Column(name = "transaction_alert_minimum", nullable = false, precision = 12, scale = 2,
            columnDefinition = "numeric(12,2) default 25")
    private BigDecimal transactionAlertMinimum = DEFAULT_ALERT_MINIMUM;

    public static final BigDecimal DEFAULT_ALERT_MINIMUM = new BigDecimal("25.00");
}
