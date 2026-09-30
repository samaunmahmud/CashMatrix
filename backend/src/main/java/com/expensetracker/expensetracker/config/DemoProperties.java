package com.expensetracker.expensetracker.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The shared demo account behind "Try the demo": made-up data anyone can look around in,
 * but never change.
 *
 * Settings (all optional):
 *   app.demo.enabled  turn the demo off (default true)
 *   app.demo.email    the demo account's email, which nobody can sign up with or log in to by password
 */
@Getter
@Component
public class DemoProperties {

    private final boolean enabled;
    private final String email;

    public DemoProperties(@Value("${app.demo.enabled:true}") boolean enabled,
                          @Value("${app.demo.email:sam.carter@cashmatrix.example}") String email) {
        this.enabled = enabled;
        this.email = email;
    }

    public boolean isDemoEmail(String candidate) {
        return candidate != null && email.equalsIgnoreCase(candidate.trim());
    }
}
