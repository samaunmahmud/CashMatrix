package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.model.Transaction;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.BudgetAlertService;
import com.expensetracker.expensetracker.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final BudgetAlertService budgetAlertService;

    @PostMapping("/sync")
    public Map sync(@AuthenticationPrincipal UserPrincipal principal) {
        int count = transactionService.syncTransactions(principal.getUser());
        // New spending may have tipped a budget over. Best-effort: the sync itself succeeded.
        try {
            budgetAlertService.check(principal.getUser());
        } catch (RuntimeException ex) {
            log.warn("Could not check budgets for user {}", principal.getUser().getId(), ex);
        }
        return Map.of("synced", count);
    }

    @GetMapping
    public List<Transaction> getTransactions(@AuthenticationPrincipal UserPrincipal principal) {
        return transactionService.getTransactionsForUser(principal.getUser());
    }
}