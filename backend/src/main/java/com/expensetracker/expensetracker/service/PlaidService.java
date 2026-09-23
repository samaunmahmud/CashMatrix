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

    public static final int PAGE_SIZE = 500;

    private final WebClient plaidWebClient;
    private final PlaidConfig plaidConfig;

    public Map createLinkToken(String userId) {
        Map<String, Object> body = Map.of(
                "client_id", plaidConfig.getClientId(),
                "secret", plaidConfig.getSecret(),
                "client_name", "CashMatrix",
                "user", Map.of("client_user_id", userId),
                "products", List.of("transactions"),
                // Two years rather than Plaid's default 90 days, so yearly subscriptions show up.
                "transactions", Map.of("days_requested", TransactionService.FULL_HISTORY_DAYS),
                "country_codes", plaidConfig.getCountryCodes(),
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

    /** One page of transactions (Plaid returns at most 500 per call); pass the running offset for the next page. */
    public Map getTransactions(String accessToken, String startDate, String endDate, int offset) {
        Map<String, Object> body = Map.of(
                "client_id", plaidConfig.getClientId(),
                "secret", plaidConfig.getSecret(),
                "access_token", accessToken,
                "start_date", startDate,
                "end_date", endDate,
                "options", Map.of("count", PAGE_SIZE, "offset", offset)
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