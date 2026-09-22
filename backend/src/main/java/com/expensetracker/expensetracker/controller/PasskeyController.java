package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.dto.AuthResponse;
import com.expensetracker.expensetracker.dto.PasskeyFinishRequest;
import com.expensetracker.expensetracker.dto.PasskeyResponse;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.PasskeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PasskeyController {

    private final PasskeyService passkeyService;

    /** Step 1 of adding a passkey: options for navigator.credentials.create(). */
    @PostMapping("/api/passkeys/register/start")
    public Map<String, Object> startRegistration(@AuthenticationPrincipal UserPrincipal principal) {
        return passkeyService.startRegistration(principal.getUser());
    }

    /** Step 2: the new passkey, checked and saved. */
    @PostMapping("/api/passkeys/register/finish")
    @ResponseStatus(HttpStatus.CREATED)
    public PasskeyResponse finishRegistration(
            @Valid @RequestBody PasskeyFinishRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return passkeyService.finishRegistration(principal.getUser(), request);
    }

    @GetMapping("/api/passkeys")
    public List<PasskeyResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return passkeyService.list(principal.getUser());
    }

    @DeleteMapping("/api/passkeys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        passkeyService.delete(principal.getUser(), id);
    }

    /** Step 1 of logging in with a passkey: options for navigator.credentials.get(). No login needed. */
    @PostMapping("/api/auth/passkey/start")
    public Map<String, Object> startLogin() {
        return passkeyService.startLogin();
    }

    /** Step 2: the signed challenge, swapped for a normal login token. */
    @PostMapping("/api/auth/passkey/finish")
    public AuthResponse finishLogin(@Valid @RequestBody PasskeyFinishRequest request) {
        return passkeyService.finishLogin(request);
    }
}
