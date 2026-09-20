package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.dto.NotificationResponse;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.NotificationService;
import com.expensetracker.expensetracker.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final ReminderService reminderService;

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        refreshReminders(principal);
        return notificationService.latest(principal.getUser());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        refreshReminders(principal);
        return Map.of("count", notificationService.unreadCount(principal.getUser()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markRead(principal.getUser(), id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("updated", notificationService.markAllRead(principal.getUser()));
    }

    /**
     * The scheduled job is the main source of reminders, but a free-tier host can be
     * asleep when it should have fired. Checking the caller's own items here means
     * they are up to date whenever the user actually looks. It is best-effort: a
     * failure must not stop the user reading the notifications they already have.
     */
    private void refreshReminders(UserPrincipal principal) {
        try {
            reminderService.generateReminders(principal.getUser());
        } catch (RuntimeException ex) {
            log.warn("Could not refresh reminders for user {}", principal.getUser().getId(), ex);
        }
    }
}
