package com.expensetracker.expensetracker.repository;

import com.expensetracker.expensetracker.model.Budget;
import com.expensetracker.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserOrderByCategoryAsc(User user);

    Optional<Budget> findByIdAndUser(Long id, User user);

    boolean existsByUserAndCategoryIgnoreCase(User user, String category);

    boolean existsByUserAndCategoryIgnoreCaseAndIdNot(User user, String category, Long id);

    // The user is fetched up front because the alert job runs in the background, with no web request to lazy-load it.
    @Query("select b from Budget b join fetch b.user")
    List<Budget> findAllWithUser();
}
