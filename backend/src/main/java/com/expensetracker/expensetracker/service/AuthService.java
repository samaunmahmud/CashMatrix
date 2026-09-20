package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.dto.AuthResponse;
import com.expensetracker.expensetracker.dto.LoginRequest;
import com.expensetracker.expensetracker.dto.SignupRequest;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.UserRepository;
import com.expensetracker.expensetracker.security.JwtService;
import com.expensetracker.expensetracker.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        // Never store the raw password - only the BCrypt hash.
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        userRepository.save(user);

        String token = jwtService.generateToken(new UserPrincipal(user));
        return new AuthResponse(token, user.getEmail(), user.getFullName());
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            // Re-thrown as IllegalArgumentException so it's caught by our
            // GlobalExceptionHandler instead of Spring Security's filter-level
            // handler, which would otherwise return an empty 403 response.
            throw new IllegalArgumentException("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException("User not found after authentication"));

        String token = jwtService.generateToken(new UserPrincipal(user));
        return new AuthResponse(token, user.getEmail(), user.getFullName());
    }
}
