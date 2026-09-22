package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.dto.SpendingInsights;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.SpendingInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final SpendingInsightsService insightsService;

    /** Spending for the last few months (1 to 12, default 6) and this month against last. */
    @GetMapping
    public SpendingInsights insights(
            @RequestParam(defaultValue = "6") int months,
            @AuthenticationPrincipal UserPrincipal principal) {
        return insightsService.insights(principal.getUser(), months);
    }
}
