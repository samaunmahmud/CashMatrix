package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.config.ResourceNotFoundException;
import com.expensetracker.expensetracker.dto.AuthResponse;
import com.expensetracker.expensetracker.dto.PasskeyFinishRequest;
import com.expensetracker.expensetracker.dto.PasskeyResponse;
import com.expensetracker.expensetracker.model.Passkey;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.PasskeyRepository;
import com.expensetracker.expensetracker.security.JwtService;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passkeys: logging in with a fingerprint, face or phone screen lock instead of a password.
 *
 * Both adding a passkey and logging in with one take two calls. "Start" hands the browser a
 * one-off challenge; the device signs it; "finish" checks the signature. Challenges live in
 * memory for a few minutes and can be used once, so a server restart just means trying again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasskeyService {

    static final Duration CHALLENGE_LIFETIME = Duration.ofMinutes(5);
    static final int MAX_PENDING = 10_000;
    static final int MAX_PASSKEYS = 10;
    private static final DateTimeFormatter ADDED = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

    private final RelyingParty relyingParty;
    private final PasskeyRepository passkeyRepository;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    private record Pending<T>(T request, Long userId, Instant expires) {}

    private final Map<String, Pending<PublicKeyCredentialCreationOptions>> registrations = new ConcurrentHashMap<>();
    private final Map<String, Pending<AssertionRequest>> logins = new ConcurrentHashMap<>();

    // --- adding a passkey ------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> startRegistration(User user) {
        if (passkeyRepository.countByUser(user) >= MAX_PASSKEYS) {
            throw new IllegalArgumentException("You can have up to " + MAX_PASSKEYS + " passkeys");
        }
        String handle = passkeyRepository.findByUserOrderByCreatedAtAsc(user).stream()
                .map(Passkey::getUserHandle).findFirst()
                .orElseGet(() -> new ByteArray(randomBytes(32)).getBase64Url());

        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(StartRegistrationOptions.builder()
                .user(UserIdentity.builder()
                        .name(user.getEmail())
                        .displayName(user.getFullName() != null ? user.getFullName() : user.getEmail())
                        .id(bytes(handle))
                        .build())
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        // Stored on the device, so logging in needs no email address.
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build())
                .timeout(CHALLENGE_LIFETIME.toMillis())
                .build());

        String requestId = remember(registrations, new Pending<>(options, user.getId(), expiry()));
        try {
            return Map.of("requestId", requestId, "options", json(options.toCredentialsCreateJson()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Transactional
    public PasskeyResponse finishRegistration(User user, PasskeyFinishRequest request) {
        Pending<PublicKeyCredentialCreationOptions> pending = take(registrations, request.getRequestId());
        if (pending == null || !pending.userId().equals(user.getId())) {
            throw new IllegalArgumentException("That request has expired. Please try again.");
        }

        RegistrationResult result;
        try {
            result = relyingParty.finishRegistration(FinishRegistrationOptions.builder()
                    .request(pending.request())
                    .response(PublicKeyCredential.parseRegistrationResponseJson(request.getCredential().toString()))
                    .build());
        } catch (RegistrationFailedException | IOException ex) {
            log.warn("Passkey registration failed for user {}: {}", user.getId(), ex.getMessage());
            throw new IllegalArgumentException("Your device's passkey couldn't be verified. Please try again.");
        }

        Passkey passkey = new Passkey();
        passkey.setUser(user);
        passkey.setCredentialId(result.getKeyId().getId().getBase64Url());
        passkey.setUserHandle(pending.request().getUser().getId().getBase64Url());
        passkey.setPublicKeyCose(result.getPublicKeyCose().getBase64Url());
        passkey.setSignatureCount(result.getSignatureCount());
        String name = request.getName() == null ? "" : request.getName().trim();
        passkey.setName(name.isEmpty() ? "Passkey added " + LocalDate.now(clock).format(ADDED) : name);
        return PasskeyResponse.from(passkeyRepository.save(passkey));
    }

    // --- logging in ------------------------------------------------------------

    public Map<String, Object> startLogin() {
        // No username: the device offers whichever CashMatrix passkeys it holds.
        AssertionRequest assertion = relyingParty.startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.REQUIRED)
                .timeout(CHALLENGE_LIFETIME.toMillis())
                .build());
        String requestId = remember(logins, new Pending<>(assertion, null, expiry()));
        try {
            return Map.of("requestId", requestId, "options", json(assertion.toCredentialsGetJson()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Transactional
    public AuthResponse finishLogin(PasskeyFinishRequest request) {
        Pending<AssertionRequest> pending = take(logins, request.getRequestId());
        if (pending == null) {
            throw new IllegalArgumentException("That request has expired. Please try again.");
        }

        AssertionResult result;
        try {
            result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(pending.request())
                    .response(PublicKeyCredential.parseAssertionResponseJson(request.getCredential().toString()))
                    .build());
        } catch (AssertionFailedException | IOException ex) {
            log.warn("Passkey login failed: {}", ex.getMessage());
            throw new IllegalArgumentException("That passkey wasn't recognised. Log in with your password instead.");
        }
        if (!result.isSuccess()) {
            throw new IllegalArgumentException("That passkey wasn't recognised. Log in with your password instead.");
        }

        Passkey passkey = passkeyRepository.findByCredentialId(result.getCredential().getCredentialId().getBase64Url())
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("That passkey wasn't recognised. Log in with your password instead."));
        passkey.setSignatureCount(result.getSignatureCount());
        passkey.setLastUsedAt(clock.instant());

        User user = passkey.getUser();
        return new AuthResponse(jwtService.generateToken(new UserPrincipal(user)), user.getEmail(), user.getFullName());
    }

    // --- managing them ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PasskeyResponse> list(User user) {
        return passkeyRepository.findByUserOrderByCreatedAtAsc(user).stream().map(PasskeyResponse::from).toList();
    }

    @Transactional
    public void delete(User user, Long id) {
        passkeyRepository.delete(passkeyRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Passkey not found")));
    }

    // --- helpers ---------------------------------------------------------------

    private <T> String remember(Map<String, Pending<T>> store, Pending<T> pending) {
        Instant now = clock.instant();
        store.values().removeIf(p -> p.expires().isBefore(now));
        if (store.size() >= MAX_PENDING) {
            throw new IllegalStateException("Too many passkey requests at once. Please try again shortly.");
        }
        String requestId = new ByteArray(randomBytes(18)).getBase64Url();
        store.put(requestId, pending);
        return requestId;
    }

    /** Removes the request, so each challenge can only ever be answered once. */
    private <T> Pending<T> take(Map<String, Pending<T>> store, String requestId) {
        Pending<T> pending = store.remove(requestId);
        return pending == null || pending.expires().isBefore(clock.instant()) ? null : pending;
    }

    private Instant expiry() {
        return clock.instant().plus(CHALLENGE_LIFETIME);
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    private JsonNode json(String raw) throws JsonProcessingException {
        return objectMapper.readTree(raw);
    }

    private static ByteArray bytes(String base64Url) {
        try {
            return ByteArray.fromBase64Url(base64Url);
        } catch (com.yubico.webauthn.data.exception.Base64UrlException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
