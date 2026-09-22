package com.expensetracker.expensetracker.dto;

import com.expensetracker.expensetracker.model.Passkey;

import java.time.Instant;

public record PasskeyResponse(Long id, String name, Instant createdAt, Instant lastUsedAt) {
    public static PasskeyResponse from(Passkey passkey) {
        return new PasskeyResponse(passkey.getId(), passkey.getName(), passkey.getCreatedAt(), passkey.getLastUsedAt());
    }
}
