package com.expensetracker.expensetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private BankAccount bankAccount;

    @Column(name = "plaid_transaction_id", unique = true)
    private String plaidTransactionId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String name;

    @Column(name = "plaid_category")
    private String plaidCategory;

    @Column(name = "user_category")
    private String userCategory;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    private Boolean pending = false;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}