package com.expensetracker.expensetracker.repository;

import com.expensetracker.expensetracker.model.PushSubscription;
import com.expensetracker.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    List<PushSubscription> findByUser(User user);
    Optional<PushSubscription> findByEndpoint(String endpoint);
    long countByUser(User user);
    void deleteByUserAndEndpoint(User user, String endpoint);
}
