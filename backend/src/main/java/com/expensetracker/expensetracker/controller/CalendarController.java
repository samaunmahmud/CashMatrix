package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.dto.CalendarEntryResponse;
import com.expensetracker.expensetracker.dto.CalendarEventRequest;
import com.expensetracker.expensetracker.dto.CalendarEventResponse;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.CalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    /** What to draw on the calendar between two dates (dates as yyyy-MM-dd). */
    @GetMapping
    public List<CalendarEntryResponse> entries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal) {
        return calendarService.getEntries(principal.getUser(), from, to);
    }

    @GetMapping("/events")
    public List<CalendarEventResponse> events(@AuthenticationPrincipal UserPrincipal principal) {
        return calendarService.listEvents(principal.getUser());
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarEventResponse create(
            @Valid @RequestBody CalendarEventRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return calendarService.create(principal.getUser(), request);
    }

    @PutMapping("/events/{id}")
    public CalendarEventResponse update(
            @PathVariable Long id,
            @Valid @RequestBody CalendarEventRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return calendarService.update(principal.getUser(), id, request);
    }

    /** Mark the current occurrence as done / paid. */
    @PostMapping("/events/{id}/complete")
    public CalendarEventResponse complete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return calendarService.complete(principal.getUser(), id);
    }

    @DeleteMapping("/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        calendarService.delete(principal.getUser(), id);
    }
}
