package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.dto.AccountResponse;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final BankAccountService bankAccountService;

    @GetMapping
    public List<AccountResponse> accounts(@AuthenticationPrincipal UserPrincipal principal) {
        return bankAccountService.getAccountsForUser(principal.getUser()).stream()
                .map(AccountResponse::from)
                .toList();
    }
}
