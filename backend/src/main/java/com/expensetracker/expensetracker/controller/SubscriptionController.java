package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.dto.CalendarEventResponse;
import com.expensetracker.expensetracker.dto.SubscriptionSuggestion;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.SubscriptionDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions/suggestions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionDetectionService detectionService;

    /** Subscriptions found in the user's transactions that aren't on their calendar yet. */
    @GetMapping
    public List<SubscriptionSuggestion> suggestions(@AuthenticationPrincipal UserPrincipal principal) {
        return detectionService.detect(principal.getUser());
    }

    /** Puts a suggestion on the calendar as a repeating subscription. */
    @PostMapping("/{key}/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarEventResponse accept(@PathVariable String key, @AuthenticationPrincipal UserPrincipal principal) {
        return detectionService.accept(principal.getUser(), key);
    }

    /** Stops a suggestion being offered again. */
    @PostMapping("/{key}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable String key, @AuthenticationPrincipal UserPrincipal principal) {
        detectionService.dismiss(principal.getUser(), key);
    }
}
