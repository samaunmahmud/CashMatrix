package com.expensetracker.expensetracker.repository;

import com.expensetracker.expensetracker.model.SavingsGoal;
import com.expensetracker.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;

import java.util.List;
import java.util.Optional;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    List<SavingsGoal> findByUserOrderByCreatedAtAscIdAsc(User user);

    Optional<SavingsGoal> findByIdAndUser(Long id, User user);

    long countByUser(User user);

    // The user is fetched up front because plans are processed in the background, with no web request to lazy-load it.
    @Query("select g from SavingsGoal g join fetch g.user where g.planNextDate <= :date")
    List<SavingsGoal> findPlansDueBy(LocalDate date);

    List<SavingsGoal> findByUserAndPlanNextDateIsNotNull(User user);
}
