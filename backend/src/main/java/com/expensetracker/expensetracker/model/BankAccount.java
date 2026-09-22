package com.expensetracker.expensetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
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

    // Last four digits of the account number, as Plaid reports them.
    @Column(length = 8)
    private String mask;

    // Balances as of the last time Plaid was asked. Null until the first fetch.
    @Column(name = "current_balance", precision = 14, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "available_balance", precision = 14, scale = 2)
    private BigDecimal availableBalance;

    @Column(name = "iso_currency_code", length = 8)
    private String currency;

    @Column(name = "balance_updated_at")
    private Instant balanceUpdatedAt;

    // When transactions were last pulled for this account. Empty until the first sync, which
    // imports months of history at once and so mustn't raise an alert for every transaction.
    @Column(name = "transactions_synced_at")
    private Instant transactionsSyncedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
