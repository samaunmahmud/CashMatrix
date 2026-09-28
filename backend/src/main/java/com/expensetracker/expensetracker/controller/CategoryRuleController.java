package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.dto.CategoryRuleResponse;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.CategoryRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/category-rules")
@RequiredArgsConstructor
public class CategoryRuleController {

    private final CategoryRuleService ruleService;

    @GetMapping
    public List<CategoryRuleResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ruleService.list(principal.getUser());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        ruleService.delete(principal.getUser(), id);
    }
}
