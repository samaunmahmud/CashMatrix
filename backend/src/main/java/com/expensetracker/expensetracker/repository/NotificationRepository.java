package com.expensetracker.expensetracker.repository;

import com.expensetracker.expensetracker.model.CalendarEvent;
import com.expensetracker.expensetracker.model.Notification;
import com.expensetracker.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUserOrderByCreatedAtDescIdDesc(User user);

    long countByUserAndReadFalse(User user);

    Optional<Notification> findByIdAndUser(Long id, User user);

    boolean existsByEventAndDueDate(CalendarEvent event, LocalDate dueDate);

    @Modifying
    @Query("update Notification n set n.read = true where n.user = :user and n.read = false")
    int markAllRead(User user);

    @Modifying
    @Query("delete from Notification n where n.event = :event")
    void deleteByEvent(CalendarEvent event);
}
