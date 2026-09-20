package com.expensetracker.expensetracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** The shape a browser's PushSubscription.toJSON() produces. */
@Getter
@Setter
public class PushSubscribeRequest {

    @NotBlank(message = "endpoint is required")
    @Size(max = 1000, message = "endpoint is too long")
    private String endpoint;

    @NotNull(message = "keys are required")
    @Valid
    private Keys keys;

    @Getter
    @Setter
    public static class Keys {
        @NotBlank(message = "p256dh is required")
        @Size(max = 200)
        private String p256dh;

        @NotBlank(message = "auth is required")
        @Size(max = 100)
        private String auth;
    }
}
