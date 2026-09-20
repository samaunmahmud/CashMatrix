package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.config.PlaidConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlaidService {

    private final WebClient plaidWebClient;
    private final PlaidConfig plaidConfig;

    public Map createLinkToken(String userId) {
        Map<String, Object> body = Map.of(
                "client_id", plaidConfig.getClientId(),
                "secret", plaidConfig.getSecret(),
                "client_name", "CashMatrix",
                "user", Map.of("client_user_id", userId),
                "products", List.of("transactions"),
                "country_codes", List.of("US"),
                "language", "en"
        );
        return callPlaid("/link/token/create", body);
    }

    public Map exchangePublicToken(String publicToken) {
        Map<String, Object> body = Map.of(
                "client_id", plaidConfig.getClientId(),
                "secret", plaidConfig.getSecret(),
                "public_token", publicToken
        );
        return callPlaid("/item/public_token/exchange", body);
    }

    public Map getAccounts(String accessToken) {
        Map<String, Object> body = Map.of(
                "client_id", plaidConfig.getClientId(),
                "secret", plaidConfig.getSecret(),
                "access_token", accessToken
        );
        return callPlaid("/accounts/get", body);
    }

    public Map getTransactions(String accessToken, String startDate, String endDate) {
        Map<String, Object> body = Map.of(
                "client_id", plaidConfig.getClientId(),
                "secret", plaidConfig.getSecret(),
                "access_token", accessToken,
                "start_date", startDate,
                "end_date", endDate,
                "options", Map.of("count", 100, "offset", 0)
        );
        return callPlaid("/transactions/get", body);
    }

    private Map callPlaid(String path, Map<String, Object> body) {
        try {
            return plaidWebClient.post()
                    .uri(path)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientResponseException ex) {
            System.err.println("Plaid error [" + path + "] status: " + ex.getStatusCode());
            System.err.println("Plaid error body: " + ex.getResponseBodyAsString());
            throw new IllegalArgumentException("Plaid error: " + ex.getResponseBodyAsString());
        } catch (Exception ex) {
            System.err.println("Unexpected error calling Plaid [" + path + "]: " + ex.getClass().getName());
            ex.printStackTrace();
            throw new IllegalArgumentException("Unexpected error: " + ex.getMessage());
        }
    }
}