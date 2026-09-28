package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.dto.TransactionResponse;
import com.expensetracker.expensetracker.dto.TransactionUpdateRequest;
import com.expensetracker.expensetracker.dto.TransactionUpdateResponse;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.BudgetAlertService;
import com.expensetracker.expensetracker.service.CategoryRuleService;
import com.expensetracker.expensetracker.service.TransactionService;
import jakarta.validation.Valid;
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
    private final CategoryRuleService categoryRuleService;

    @PostMapping("/sync")
    public Map sync(@AuthenticationPrincipal UserPrincipal principal) {
        int count = transactionService.syncTransactions(principal.getUser());
        // New spending may have tipped a budget over.
        checkBudgets(principal);
        return Map.of("synced", count);
    }

    /** Re-files a transaction under another category, adds a note, and optionally makes a rule for its retailer. */
    @PutMapping("/{id}")
    public TransactionUpdateResponse update(
            @PathVariable Long id,
            @Valid @RequestBody TransactionUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TransactionUpdateResponse response = categoryRuleService.update(principal.getUser(), id, request);
        // Moving spending into a budgeted category may have tipped it over.
        checkBudgets(principal);
        return response;
    }

    @GetMapping
    public List<TransactionResponse> getTransactions(@AuthenticationPrincipal UserPrincipal principal) {
        return transactionService.getTransactionsForUser(principal.getUser()).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    // Best-effort: whatever called this has already succeeded.
    private void checkBudgets(UserPrincipal principal) {
        try {
            budgetAlertService.check(principal.getUser());
        } catch (RuntimeException ex) {
            log.warn("Could not check budgets for user {}", principal.getUser().getId(), ex);
        }
    }
}
