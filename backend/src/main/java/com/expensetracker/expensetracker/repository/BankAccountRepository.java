package com.expensetracker.expensetracker.repository;

import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    List<BankAccount> findByUser(User user);
    Optional<BankAccount> findByPlaidAccountId(String plaidAccountId);
}