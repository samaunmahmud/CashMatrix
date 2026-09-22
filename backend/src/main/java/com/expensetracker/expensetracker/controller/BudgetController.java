package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.dto.BudgetOverview;
import com.expensetracker.expensetracker.dto.BudgetRequest;
import com.expensetracker.expensetracker.dto.BudgetResponse;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.BudgetAlertService;
import com.expensetracker.expensetracker.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.YearMonth;

@Slf4j
@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;
    private final BudgetAlertService alertService;
    private final Clock clock;

    /** Every budget and how it is doing in a month (yyyy-MM, default this month). */
    @GetMapping
    public BudgetOverview overview(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @AuthenticationPrincipal UserPrincipal principal) {
        return budgetService.overview(principal.getUser(), month != null ? month : YearMonth.now(clock));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetResponse create(@Valid @RequestBody BudgetRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        BudgetResponse created = budgetService.create(principal.getUser(), request);
        checkAlerts(principal);
        return created;
    }

    @PutMapping("/{id}")
    public BudgetResponse update(
            @PathVariable Long id,
            @Valid @RequestBody BudgetRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        BudgetResponse updated = budgetService.update(principal.getUser(), id, request);
        checkAlerts(principal);
        return updated;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        budgetService.delete(principal.getUser(), id);
    }

    // A new or lowered limit may already be passed. Best-effort: the budget is saved either way.
    private void checkAlerts(UserPrincipal principal) {
        try {
            alertService.check(principal.getUser());
        } catch (RuntimeException ex) {
            log.warn("Could not check budgets for user {}", principal.getUser().getId(), ex);
        }
    }
}
