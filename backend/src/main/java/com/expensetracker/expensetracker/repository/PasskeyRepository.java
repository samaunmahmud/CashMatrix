package com.expensetracker.expensetracker.repository;

import com.expensetracker.expensetracker.model.Passkey;
import com.expensetracker.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasskeyRepository extends JpaRepository<Passkey, Long> {
    List<Passkey> findByUserOrderByCreatedAtAsc(User user);
    List<Passkey> findByUserEmail(String email);
    List<Passkey> findByUserHandle(String userHandle);
    List<Passkey> findByCredentialId(String credentialId);
    Optional<Passkey> findByIdAndUser(Long id, User user);
    long countByUser(User user);
}
