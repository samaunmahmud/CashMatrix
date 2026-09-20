package com.expensetracker.expensetracker.repository;

import com.expensetracker.expensetracker.model.DismissedSuggestion;
import com.expensetracker.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DismissedSuggestionRepository extends JpaRepository<DismissedSuggestion, Long> {

    List<DismissedSuggestion> findByUser(User user);

    boolean existsByUserAndMerchantKey(User user, String merchantKey);
}
