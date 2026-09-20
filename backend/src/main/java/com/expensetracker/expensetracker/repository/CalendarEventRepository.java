package com.expensetracker.expensetracker.repository;

import com.expensetracker.expensetracker.model.CalendarEvent;
import com.expensetracker.expensetracker.model.EventType;
import com.expensetracker.expensetracker.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    List<CalendarEvent> findByUserOrderByNextDueDateAsc(User user);

    // Anything that starts after the requested window can't appear in it.
    List<CalendarEvent> findByUserAndStartDateLessThanEqual(User user, LocalDate to);

    Optional<CalendarEvent> findByIdAndUser(Long id, User user);

    // The reminder job reads the user off each event, so fetch it up front
    // (the job runs outside a web request, where lazy loading has no session).
    @EntityGraph(attributePaths = "user")
    List<CalendarEvent> findByNextDueDateBetween(LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = "user")
    List<CalendarEvent> findByUserAndNextDueDateBetween(User user, LocalDate from, LocalDate to);

    List<CalendarEvent> findByTypeAndNextDueDateBefore(EventType type, LocalDate date);
}
