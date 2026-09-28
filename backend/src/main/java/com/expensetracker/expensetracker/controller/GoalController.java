package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.dto.ContributionRequest;
import com.expensetracker.expensetracker.dto.GoalRequest;
import com.expensetracker.expensetracker.dto.GoalResponse;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.GoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    public List<GoalResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return goalService.list(principal.getUser());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@Valid @RequestBody GoalRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return goalService.create(principal.getUser(), request);
    }

    @PutMapping("/{id}")
    public GoalResponse update(
            @PathVariable Long id,
            @Valid @RequestBody GoalRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return goalService.update(principal.getUser(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        goalService.delete(principal.getUser(), id);
    }

    /** Money put towards the goal, or taken out of it when negative. */
    @PostMapping("/{id}/contributions")
    public GoalResponse contribute(
            @PathVariable Long id,
            @Valid @RequestBody ContributionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return goalService.contribute(principal.getUser(), id, request);
    }
}
