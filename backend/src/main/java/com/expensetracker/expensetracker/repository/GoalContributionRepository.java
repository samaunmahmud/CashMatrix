package com.expensetracker.expensetracker.repository;

import com.expensetracker.expensetracker.model.GoalContribution;
import com.expensetracker.expensetracker.model.SavingsGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, Long> {

    List<GoalContribution> findTop5ByGoalOrderByMadeOnDescIdDesc(SavingsGoal goal);

    @Modifying
    @Query("delete from GoalContribution c where c.goal = :goal")
    void deleteByGoal(SavingsGoal goal);
}
