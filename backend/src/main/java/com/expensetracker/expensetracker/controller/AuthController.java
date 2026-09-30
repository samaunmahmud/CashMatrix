package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.dto.AuthResponse;
import com.expensetracker.expensetracker.dto.LoginRequest;
import com.expensetracker.expensetracker.dto.SignupRequest;
import com.expensetracker.expensetracker.service.AuthService;
import com.expensetracker.expensetracker.service.DemoAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final DemoAccountService demoAccountService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** "Try the demo": logs the visitor in to a shared account full of made-up data, which is read-only. */
    @PostMapping("/demo")
    public ResponseEntity<AuthResponse> demo() {
        return ResponseEntity.ok(demoAccountService.login());
    }
}
