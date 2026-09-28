package com.expensetracker.expensetracker.repository;

import com.expensetracker.expensetracker.model.GoalContribution;
import com.expensetracker.expensetracker.model.SavingsGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, Long> {

    List<GoalContribution> findTop5ByGoalOrderByMadeOnDescIdDesc(SavingsGoal goal);

    @Query("select coalesce(sum(c.amount), 0) from GoalContribution c where c.goal.user = :user and c.madeOn between :from and :to")
    java.math.BigDecimal totalForUserBetween(com.expensetracker.expensetracker.model.User user, java.time.LocalDate from, java.time.LocalDate to);

    @Modifying
    @Query("delete from GoalContribution c where c.goal = :goal")
    void deleteByGoal(SavingsGoal goal);
}
