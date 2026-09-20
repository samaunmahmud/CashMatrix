package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.BankAccountService;
import com.expensetracker.expensetracker.service.PlaidService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/plaid")
@RequiredArgsConstructor
public class PlaidController {

    private final PlaidService plaidService;
    private final BankAccountService bankAccountService;

    @PostMapping("/link-token")
    public Map createLinkToken(@AuthenticationPrincipal UserPrincipal principal) {
        return plaidService.createLinkToken(String.valueOf(principal.getUser().getId()));
    }

    @PostMapping("/exchange-token")
    public Map exchangeToken(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal principal) {

        String publicToken = request.get("public_token");
        Map exchangeResult = plaidService.exchangePublicToken(publicToken);
        String accessToken = (String) exchangeResult.get("access_token");
        String itemId = (String) exchangeResult.get("item_id");

        Map accountsResult = plaidService.getAccounts(accessToken);
        bankAccountService.saveAccounts(principal.getUser(), accessToken, itemId, accountsResult);

        return Map.of("status", "connected");
    }
}