package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.dto.MonthlySummary;
import com.expensetracker.expensetracker.dto.SpendingInsights;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.MonthlySummaryService;
import com.expensetracker.expensetracker.service.SpendingInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final SpendingInsightsService insightsService;
    private final MonthlySummaryService summaryService;
    private final Clock clock;

    /** Spending for the last few months (1 to 12, default 6) and this month against last. */
    @GetMapping
    public SpendingInsights insights(
            @RequestParam(defaultValue = "6") int months,
            @AuthenticationPrincipal UserPrincipal principal) {
        return insightsService.insights(principal.getUser(), months);
    }

    /** A look back at one month (yyyy-MM, default last month). */
    @GetMapping("/summary")
    public MonthlySummary summary(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @AuthenticationPrincipal UserPrincipal principal) {
        return summaryService.summary(principal.getUser(), month != null ? month : YearMonth.now(clock).minusMonths(1));
    }
}
