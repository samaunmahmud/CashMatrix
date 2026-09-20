package com.expensetracker.expensetracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class PlaidConfig {

    @Value("${plaid.client-id}")
    private String clientId;

    @Value("${plaid.secret}")
    private String secret;

    @Value("${plaid.env}")
    private String env;

    public String getClientId() {
        return clientId;
    }

    public String getSecret() {
        return secret;
    }

    public String getBaseUrl() {
        return switch (env) {
            case "production" -> "https://production.plaid.com";
            case "development" -> "https://development.plaid.com";
            default -> "https://sandbox.plaid.com";
        };
    }

    @Bean
    public WebClient plaidWebClient() {
        return WebClient.builder()
                .baseUrl(getBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}