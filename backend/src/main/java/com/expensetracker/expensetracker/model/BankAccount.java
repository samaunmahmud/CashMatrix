package com.expensetracker.expensetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Represents one bank account a user has connected via Plaid.
 * A user can have multiple linked accounts (checking, savings, credit card, etc).
 */
@Entity
@Table(name = "bank_accounts")
@Getter
@Setter
@NoArgsConstructor
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Plaid's permanent token for this user's linked bank login.
    // Needed to fetch transactions later. Treat like a secret.
    @Column(name = "plaid_access_token", nullable = false)
    private String plaidAccessToken;

    // Plaid's unique id for the bank connection (institution-level)
    @Column(name = "plaid_item_id", nullable = false)
    private String plaidItemId;

    // Plaid's unique id for this specific account (e.g. one checking account)
    @Column(name = "plaid_account_id", nullable = false, unique = true)
    private String plaidAccountId;

    private String name; // e.g. "Plaid Checking"

    @Column(name = "official_name")
    private String officialName;

    private String type; // depository, credit, etc.
    private String subtype; // checking, savings, etc.

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
