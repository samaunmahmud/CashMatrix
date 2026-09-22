package com.expensetracker.expensetracker.security.passkey;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Passkeys are tied to the website's domain, so the server must know the address the app is
 * served from. It comes from app.public-url (the same setting the email links use).
 *
 * Settings (optional):
 *   app.webauthn.rp-id           the domain passkeys belong to (default: app.public-url's host)
 *   app.webauthn.extra-origins   more addresses allowed to use them, comma separated
 */
@Configuration
public class PasskeyConfig {

    @Bean
    public RelyingParty relyingParty(
            PasskeyCredentialRepository credentials,
            @Value("${app.public-url:http://localhost:5173}") String publicUrl,
            @Value("${app.webauthn.rp-id:}") String rpId,
            @Value("${app.webauthn.extra-origins:}") String extraOrigins) {

        URI app = URI.create(publicUrl.trim());
        Set<String> origins = new LinkedHashSet<>();
        origins.add(app.getScheme() + "://" + app.getAuthority());
        Arrays.stream(extraOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(origins::add);

        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(rpId.isBlank() ? app.getHost() : rpId.trim())
                        .name("CashMatrix")
                        .build())
                .credentialRepository(credentials)
                .origins(origins)
                .build();
    }
}
