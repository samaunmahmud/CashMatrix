package com.expensetracker.expensetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private String fullName;
    // True for the shared demo account, so the app can say that changes aren't saved.
    private boolean demo;

    public AuthResponse(String token, String email, String fullName) {
        this(token, email, fullName, false);
    }
}
