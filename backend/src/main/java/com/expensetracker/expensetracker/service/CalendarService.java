package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.config.ResourceNotFoundException;
import com.expensetracker.expensetracker.dto.CalendarEntryResponse;
import com.expensetracker.expensetracker.dto.CalendarEventRequest;
import com.expensetracker.expensetracker.dto.CalendarEventResponse;
import com.expensetracker.expensetracker.model.CalendarEvent;
import com.expensetracker.expensetracker.model.Recurrence;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.CalendarEventRepository;
import com.expensetracker.expensetracker.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarService {

    // Guards against a request that would expand years of weekly items at once.
    static final int MAX_RANGE_DAYS = 400;
    static final int DEFAULT_REMIND_DAYS = 1;

    private final CalendarEventRepository eventRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    /** Every occurrence between two dates, ready to draw on a calendar. */
    @Transactional(readOnly = true)
    public List<CalendarEntryResponse> getEntries(User user, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must not be before 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Date range can't be longer than " + MAX_RANGE_DAYS + " days");
        }

        List<CalendarEntryResponse> entries = new ArrayList<>();
        for (CalendarEvent event : eventRepository.findByUserAndStartDateLessThanEqual(user, to)) {
            Recurrence recurrence = event.getRecurrence();
            for (LocalDate date : recurrence.occurrencesBetween(event.getStartDate(), from, to)) {
                // Occurrences before nextDueDate have already been dealt with.
                boolean done = recurrence.isRecurring()
                        ? date.isBefore(event.getNextDueDate())
                        : event.isCompleted();
                entries.add(new CalendarEntryResponse(
                        event.getId(), event.getTitle(), event.getDescription(), event.getType(),
                        event.getAmount(), date, recurrence, done));
            }
        }
        entries.sort(Comparator.comparing(CalendarEntryResponse::date)
                .thenComparing(CalendarEntryResponse::title, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> listEvents(User user) {
        return eventRepository.findByUserOrderByNextDueDateAsc(user).stream()
                .map(CalendarEventResponse::from)
                .toList();
    }

    @Transactional
    public CalendarEventResponse create(User user, CalendarEventRequest request) {
        CalendarEvent event = new CalendarEvent();
        event.setUser(user);
        apply(event, request);
        event.setNextDueDate(firstDueDate(event));
        return CalendarEventResponse.from(eventRepository.save(event));
    }

    @Transactional
    public CalendarEventResponse update(User user, Long id, CalendarEventRequest request) {
        CalendarEvent event = find(user, id);
        LocalDate oldStart = event.getStartDate();
        Recurrence oldRecurrence = event.getRecurrence();

        apply(event, request);

        // Editing only the title or amount must not disturb where a repeating item
        // has got to. Changing the date or the repeat pattern starts it afresh.
        if (!oldStart.equals(event.getStartDate()) || oldRecurrence != event.getRecurrence()) {
            event.setCompleted(false);
            event.setNextDueDate(firstDueDate(event));
        }
        return CalendarEventResponse.from(event);
    }

    /**
     * Marks the current occurrence done. A one-off item is finished; a repeating
     * item moves on to its next occurrence.
     */
    @Transactional
    public CalendarEventResponse complete(User user, Long id) {
        CalendarEvent event = find(user, id);
        if (event.getRecurrence().isRecurring()) {
            event.setNextDueDate(event.getRecurrence().firstAfter(event.getStartDate(), event.getNextDueDate()));
        } else {
            event.setCompleted(true);
        }
        return CalendarEventResponse.from(event);
    }

    @Transactional
    public void delete(User user, Long id) {
        CalendarEvent event = find(user, id);
        notificationRepository.deleteByEvent(event);
        eventRepository.delete(event);
    }

    private CalendarEvent find(User user, Long id) {
        return eventRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar item not found"));
    }

    private void apply(CalendarEvent event, CalendarEventRequest request) {
        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription());
        event.setType(request.getType());
        event.setAmount(request.getAmount());
        event.setStartDate(request.getStartDate());
        event.setRecurrence(request.getRecurrence() != null ? request.getRecurrence() : Recurrence.NONE);
        event.setRemindDaysBefore(
                request.getRemindDaysBefore() != null ? request.getRemindDaysBefore() : DEFAULT_REMIND_DAYS);
    }

    /**
     * A repeating item entered with an old start date (say a subscription that began
     * last year) is waiting on its next occurrence from today, not on the start date.
     */
    private LocalDate firstDueDate(CalendarEvent event) {
        if (event.getRecurrence().isRecurring()) {
            return event.getRecurrence().firstOnOrAfter(event.getStartDate(), LocalDate.now(clock));
        }
        return event.getStartDate();
    }
}
