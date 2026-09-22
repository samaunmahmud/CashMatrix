package com.expensetracker.expensetracker.repository;

import com.expensetracker.expensetracker.model.CalendarEvent;
import com.expensetracker.expensetracker.model.Notification;
import com.expensetracker.expensetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUserOrderByCreatedAtDescIdDesc(User user);

    long countByUserAndReadFalse(User user);

    Optional<Notification> findByIdAndUser(Long id, User user);

    boolean existsByEventAndDueDate(CalendarEvent event, LocalDate dueDate);

    boolean existsByAlertKey(String alertKey);

    // Recent alerts that haven't gone out on every channel yet. The user and event are fetched
    // up front because delivery runs in the background, with no web request to lazy-load them.
    @Query("select n from Notification n join fetch n.user left join fetch n.event "
            + "where n.createdAt > :since and (n.emailSent = false or n.pushSent = false)")
    List<Notification> findUndelivered(Instant since);

    @Modifying
    @Query("update Notification n set n.read = true where n.user = :user and n.read = false")
    int markAllRead(User user);

    @Modifying
    @Query("delete from Notification n where n.event = :event")
    void deleteByEvent(CalendarEvent event);
}
