package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.model.Transaction;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/sync")
    public Map sync(@AuthenticationPrincipal UserPrincipal principal) {
        int count = transactionService.syncTransactions(principal.getUser());
        return Map.of("synced", count);
    }

    @GetMapping
    public List<Transaction> getTransactions(@AuthenticationPrincipal UserPrincipal principal) {
        return transactionService.getTransactionsForUser(principal.getUser());
    }
}