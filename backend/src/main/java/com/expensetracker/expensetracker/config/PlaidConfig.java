package com.expensetracker.expensetracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;

@Configuration
public class PlaidConfig {

    @Value("${plaid.client-id}")
    private String clientId;

    @Value("${plaid.secret}")
    private String secret;

    @Value("${plaid.env}")
    private String env;

    // Comma separated ISO country codes of the banks users may link, e.g. "GB" or "GB,US".
    @Value("${plaid.country-codes:GB}")
    private String countryCodes;

    public List<String> getCountryCodes() {
        return Arrays.stream(countryCodes.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(String::toUpperCase)
                .toList();
    }

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