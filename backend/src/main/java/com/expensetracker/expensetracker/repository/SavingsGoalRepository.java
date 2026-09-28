package com.expensetracker.expensetracker.repository;

import com.expensetracker.expensetracker.model.SavingsGoal;
import com.expensetracker.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    List<SavingsGoal> findByUserOrderByCreatedAtAscIdAsc(User user);

    Optional<SavingsGoal> findByIdAndUser(Long id, User user);

    long countByUser(User user);
}
