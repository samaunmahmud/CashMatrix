package com.expensetracker.expensetracker.controller;

import com.expensetracker.expensetracker.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/me")
    public Map<String, String> me(@AuthenticationPrincipal UserPrincipal principal) {
        Map<String, String> response = new HashMap<>();
        response.put("email", principal.getUser().getEmail());
        response.put("fullName", principal.getUser().getFullName());
        return response;
    }
}