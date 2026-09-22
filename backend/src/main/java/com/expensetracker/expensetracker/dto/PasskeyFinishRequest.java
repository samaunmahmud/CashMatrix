package com.expensetracker.expensetracker.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * The second half of adding a passkey or logging in with one.
 *
 * @see com.expensetracker.expensetracker.service.PasskeyService
 */
@Getter
@Setter
public class PasskeyFinishRequest {

    @NotBlank(message = "requestId is required")
    private String requestId;

    // What the browser's navigator.credentials call produced, in its standard JSON form.
    @NotNull(message = "credential is required")
    private JsonNode credential;

    // Only when adding one: a label the user will recognise, like "iPhone".
    @Size(max = 80, message = "Name must be 80 characters or fewer")
    private String name;
}
