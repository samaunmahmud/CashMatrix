package com.expensetracker.expensetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * "Always file Tesco under Groceries": a category the user chose for every transaction from one retailer.
 *
 * The retailer is matched by its key (the retailer name in lower case, see MerchantNames.retailer),
 * so "Tesco Express" and "TESCO STORES 2041" follow the same rule. Rules are applied to what is already
 * stored when they are made, and to new transactions as they are synced.
 */
@Entity
@Table(name = "category_rules", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "merchant_key"}))
@Getter
@Setter
@NoArgsConstructor
public class CategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "merchant_key", nullable = false, length = 120)
    private String merchantKey;

    // The retailer as the app shows it, for listing the rule.
    @Column(name = "merchant_name", nullable = false, length = 120)
    private String merchantName;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
