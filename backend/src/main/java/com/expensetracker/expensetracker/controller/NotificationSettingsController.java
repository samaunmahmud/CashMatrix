package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.dto.NotificationSettingsRequest;
import com.expensetracker.expensetracker.dto.NotificationSettingsResponse;
import com.expensetracker.expensetracker.dto.PushSubscribeRequest;
import com.expensetracker.expensetracker.dto.PushUnsubscribeRequest;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.delivery.NotificationSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class NotificationSettingsController {

    private final NotificationSettingsService settingsService;

    @GetMapping("/api/settings/notifications")
    public NotificationSettingsResponse get(@AuthenticationPrincipal UserPrincipal principal) {
        return settingsService.get(principal.getUser());
    }

    @PutMapping("/api/settings/notifications")
    public NotificationSettingsResponse update(
            @Valid @RequestBody NotificationSettingsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return settingsService.update(principal.getUser(), request);
    }

    /** Sends a test reminder through the channels that are switched on. */
    @PostMapping("/api/settings/notifications/test")
    public Map<String, String> test(@AuthenticationPrincipal UserPrincipal principal) {
        return settingsService.sendTest(principal.getUser());
    }

    @PostMapping("/api/push/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(
            @Valid @RequestBody PushSubscribeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        settingsService.subscribe(principal.getUser(), request);
    }

    @PostMapping("/api/push/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(
            @Valid @RequestBody PushUnsubscribeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        settingsService.unsubscribe(principal.getUser(), request.getEndpoint());
    }
}
