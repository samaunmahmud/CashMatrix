package com.expensetracker.expensetracker.repository;

import com.expensetracker.expensetracker.model.CategoryRule;
import com.expensetracker.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

    List<CategoryRule> findByUserOrderByMerchantNameAsc(User user);

    Optional<CategoryRule> findByUserAndMerchantKey(User user, String merchantKey);

    Optional<CategoryRule> findByIdAndUser(Long id, User user);
}
