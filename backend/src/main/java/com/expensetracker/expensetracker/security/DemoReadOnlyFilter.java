package com.expensetracker.expensetracker.security;

import com.expensetracker.expensetracker.config.DemoProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Keeps the shared demo account read-only: everyone who tries the demo sees the same data,
 * so one visitor must not be able to change or delete it for the next.
 */
public class DemoReadOnlyFilter extends OncePerRequestFilter {

    public static final String MESSAGE = "This is the demo, so changes aren't saved. Sign up to try it with your own accounts.";

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    // Refreshing only tops the demo data up to today, so the Sync button can work.
    private static final Set<String> ALLOWED_WRITES = Set.of("/api/transactions/sync");

    private final DemoProperties demo;

    public DemoReadOnlyFilter(DemoProperties demo) {
        this.demo = demo;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (isWrite(request) && isDemoUser()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + MESSAGE + "\",\"demo\":true}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isWrite(HttpServletRequest request) {
        return !READ_METHODS.contains(request.getMethod()) && !ALLOWED_WRITES.contains(request.getRequestURI());
    }

    private boolean isDemoUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof UserPrincipal principal
                && demo.isDemoEmail(principal.getUsername());
    }
}
