package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.Passkey;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.PasskeyRepository;
import com.expensetracker.expensetracker.repository.UserRepository;
import com.expensetracker.expensetracker.security.JwtService;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The parts of passkeys that don't need a real device. The full sign-up-and-log-in round trip
 * needs an authenticator to sign challenges, so it is checked in a browser with a virtual one.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasskeyApiTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired PasskeyRepository passkeyRepository;
    @Autowired ObjectMapper objectMapper;

    User user;

    @BeforeEach
    void newUser() {
        user = saveUser();
    }

    @Test
    void registrationAsksForAPasskeyStoredOnTheDeviceAndUnlockedByTheUser() throws Exception {
        mvc.perform(post("/api/passkeys/register/start").header("Authorization", auth(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.options.publicKey.rp.id").value("localhost"))
                .andExpect(jsonPath("$.options.publicKey.rp.name").value("CashMatrix"))
                .andExpect(jsonPath("$.options.publicKey.user.name").value(user.getEmail()))
                .andExpect(jsonPath("$.options.publicKey.challenge").isNotEmpty())
                .andExpect(jsonPath("$.options.publicKey.authenticatorSelection.residentKey").value("required"))
                .andExpect(jsonPath("$.options.publicKey.authenticatorSelection.userVerification").value("required"));

        mvc.perform(post("/api/passkeys/register/start")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginNeedsNoAccountButEachChallengeIsFreshAndSingleUse() throws Exception {
        JsonNode first = start("/api/auth/passkey/start", null);
        JsonNode second = start("/api/auth/passkey/start", null);
        assertThat(first.at("/options/publicKey/challenge").asText())
                .isNotEqualTo(second.at("/options/publicKey/challenge").asText());
        assertThat(first.at("/options/publicKey/rpId").asText()).isEqualTo("localhost");

        // A made-up signature is refused, and the challenge it answered is then gone.
        String requestId = first.get("requestId").asText();
        finish("/api/auth/passkey/finish", null, requestId).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("That passkey wasn't recognised. Log in with your password instead."));
        finish("/api/auth/passkey/finish", null, requestId).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("That request has expired. Please try again."));
    }

    @Test
    void aRegistrationStartedByOneUserCantBeFinishedByAnother() throws Exception {
        String requestId = start("/api/passkeys/register/start", user).get("requestId").asText();
        User other = saveUser();
        finish("/api/passkeys/register/finish", other, requestId).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("That request has expired. Please try again."));
        finish("/api/passkeys/register/finish", other, "made-up").andExpect(status().isBadRequest());
        mvc.perform(post("/api/passkeys/register/finish").header("Authorization", auth(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void usersSeeAndRemoveOnlyTheirOwnPasskeys() throws Exception {
        Passkey mine = passkey(user, "iPhone");
        Passkey theirs = passkey(saveUser(), "Pixel");

        mvc.perform(get("/api/passkeys").header("Authorization", auth(user)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("iPhone"))
                .andExpect(jsonPath("$[0].publicKeyCose").doesNotExist())
                .andExpect(jsonPath("$[0].credentialId").doesNotExist());

        mvc.perform(delete("/api/passkeys/" + theirs.getId()).header("Authorization", auth(user)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/passkeys/" + mine.getId()).header("Authorization", auth(user)))
                .andExpect(status().isNoContent());
        assertThat(passkeyRepository.findById(theirs.getId())).isPresent();
        assertThat(passkeyRepository.findById(mine.getId())).isEmpty();
    }

    // --- helpers -------------------------------------------------------------

    private JsonNode start(String path, User u) throws Exception {
        var request = post(path);
        if (u != null) request.header("Authorization", auth(u));
        String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private org.springframework.test.web.servlet.ResultActions finish(String path, User u, String requestId) throws Exception {
        String fakeCredential = "{\"id\":\"AAAA\",\"rawId\":\"AAAA\",\"type\":\"public-key\",\"clientExtensionResults\":{},"
                + "\"response\":{\"clientDataJSON\":\"e30\",\"authenticatorData\":\"AAAA\",\"signature\":\"AAAA\",\"attestationObject\":\"AAAA\"}}";
        var request = post(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"" + requestId + "\",\"credential\":" + fakeCredential + "}");
        if (u != null) request.header("Authorization", auth(u));
        return mvc.perform(request);
    }

    private Passkey passkey(User owner, String name) {
        Passkey p = new Passkey();
        p.setUser(owner);
        p.setName(name);
        p.setCredentialId("cred-" + UUID.randomUUID());
        p.setUserHandle("handle-" + owner.getId());
        p.setPublicKeyCose("AAAA");
        return passkeyRepository.save(p);
    }

    private User saveUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@example.com");
        u.setPasswordHash("x");
        u.setFullName("Passkey Test");
        return userRepository.save(u);
    }

    private String auth(User u) {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(u));
    }
}
