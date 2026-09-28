package com.expensetracker.expensetracker.dto;

import com.expensetracker.expensetracker.model.CategoryRule;

public record CategoryRuleResponse(Long id, String retailer, String category) {
    public static CategoryRuleResponse from(CategoryRule rule) {
        return new CategoryRuleResponse(rule.getId(), rule.getMerchantName(), rule.getCategory());
    }
}
