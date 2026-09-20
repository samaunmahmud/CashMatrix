package com.expensetracker.expensetracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PushUnsubscribeRequest {

    @NotBlank(message = "endpoint is required")
    private String endpoint;
}
