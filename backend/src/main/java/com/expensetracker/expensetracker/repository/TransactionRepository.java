package com.expensetracker.expensetracker.repository;

import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByBankAccountInOrderByTransactionDateDesc(List<BankAccount> bankAccounts);
    Optional<Transaction> findByPlaidTransactionId(String plaidTransactionId);
}
