package com.expensetracker.expensetracker.repository;

import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    List<BankAccount> findByUser(User user);
    Optional<BankAccount> findByPlaidAccountId(String plaidAccountId);
    Optional<BankAccount> findByIdAndUser(Long id, User user);

    @Query("select distinct a.user from BankAccount a")
    List<User> findUsersWithAccounts();

    @Transactional
    @Modifying
    @Query("update BankAccount a set a.transactionsSyncedAt = :at where a.user = :user")
    void markSynced(User user, Instant at);
}