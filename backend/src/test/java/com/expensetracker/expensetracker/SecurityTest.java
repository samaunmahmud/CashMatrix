package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.security.JwtService;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwtService;
    @Value("${jwt.secret}") String secret;

    private String tokenFor(String email, Date issuedAt, Date expiresAt) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String expiredToken(String email) {
        long now = System.currentTimeMillis();
        return tokenFor(email, new Date(now - 2 * 86_400_000L), new Date(now - 86_400_000L));
    }

    @Test
    void protectedEndpointsAnswer401WithAJsonMessageWhenNotLoggedIn() throws Exception {
        mvc.perform(get("/api/calendar/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Please log in again"));
    }

    @Test
    void anExpiredTokenIsRejectedWith401NotAServerError() throws Exception {
        mvc.perform(get("/api/calendar/events").header("Authorization", "Bearer " + expiredToken("someone@example.com")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aGarbageTokenIsRejectedWith401() throws Exception {
        mvc.perform(get("/api/calendar/events").header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aValidTokenForAnAccountThatNoLongerExistsIsRejectedWith401() throws Exception {
        User ghost = new User();
        ghost.setEmail(UUID.randomUUID() + "@example.com");
        ghost.setPasswordHash("x");
        String token = jwtService.generateToken(new UserPrincipal(ghost));

        mvc.perform(get("/api/calendar/events").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aStaleTokenInTheBrowserDoesNotStopSomeoneLoggingInAgain() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "password123", "fullName", "Stale Token"))))
                .andExpect(status().isOk());

        // The frontend attaches whatever token it has to every request, including the login itself.
        mvc.perform(post("/api/auth/login")
                        .header("Authorization", "Bearer " + expiredToken(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
