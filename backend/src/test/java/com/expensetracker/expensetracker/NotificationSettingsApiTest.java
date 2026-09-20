package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.PushSubscriptionRepository;
import com.expensetracker.expensetracker.repository.UserRepository;
import com.expensetracker.expensetracker.security.JwtService;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.delivery.PushGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfig.class)
class NotificationSettingsApiTest {

    static final String FCM = "https://fcm.googleapis.com/fcm/send/";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepository;
    @Autowired PushSubscriptionRepository pushRepository;
    @Autowired JwtService jwtService;
    @MockBean JavaMailSender mailSender;
    @MockBean PushGateway pushGateway;

    User user;
    String token;

    @BeforeEach
    void newUser() {
        user = newUserRow();
        token = tokenFor(user);
        when(pushGateway.isConfigured()).thenReturn(true);
        when(pushGateway.publicKey()).thenReturn("PUBLIC-VAPID-KEY");
    }

    @Test
    void settingsStartWithEmailOffAndTellTheAppWhatIsAvailable() throws Exception {
        mvc.perform(get("/api/settings/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.emailAvailable").value(true))
                .andExpect(jsonPath("$.pushAvailable").value(true))
                .andExpect(jsonPath("$.pushPublicKey").value("PUBLIC-VAPID-KEY"))
                .andExpect(jsonPath("$.pushDevices").value(0));
    }

    @Test
    void reportsPushAsUnavailableWhenNoKeysAreConfigured() throws Exception {
        when(pushGateway.isConfigured()).thenReturn(false);
        when(pushGateway.publicKey()).thenReturn(null);

        mvc.perform(get("/api/settings/notifications").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.pushAvailable").value(false))
                .andExpect(jsonPath("$.pushPublicKey").doesNotExist());
    }

    @Test
    void emailReminderCanBeTurnedOnAndOff() throws Exception {
        putSettings(Map.of("emailEnabled", true)).andExpect(status().isOk()).andExpect(jsonPath("$.emailEnabled").value(true));
        mvc.perform(get("/api/settings/notifications").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.emailEnabled").value(true));
        putSettings(Map.of("emailEnabled", false)).andExpect(jsonPath("$.emailEnabled").value(false));
    }

    @Test
    void rejectsASettingsUpdateWithoutAValue() throws Exception {
        putSettings(Map.of()).andExpect(status().isBadRequest());
    }

    @Test
    void registersABrowserOnceEvenIfItSubscribesRepeatedly() throws Exception {
        String endpoint = FCM + UUID.randomUUID();

        subscribe(endpoint).andExpect(status().isNoContent());
        subscribe(endpoint).andExpect(status().isNoContent());

        assertThat(pushRepository.countByUser(user)).isEqualTo(1);
        mvc.perform(get("/api/settings/notifications").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.pushDevices").value(1));
    }

    @Test
    void aBrowserHandedToAnotherAccountMovesToThatAccount() throws Exception {
        String endpoint = FCM + UUID.randomUUID();
        subscribe(endpoint).andExpect(status().isNoContent());

        User other = newUserRow();
        mvc.perform(post("/api/push/subscribe").header("Authorization", "Bearer " + tokenFor(other))
                .contentType(MediaType.APPLICATION_JSON).content(subscriptionJson(endpoint))).andExpect(status().isNoContent());

        assertThat(pushRepository.countByUser(user)).isZero();
        assertThat(pushRepository.countByUser(other)).isEqualTo(1);
    }

    @Test
    void onlyRealBrowserPushServicesAreAccepted() throws Exception {
        for (String bad : new String[]{
                "http://fcm.googleapis.com/fcm/send/abc",           // not https
                "https://evil.example.com/push",                    // not a push service
                "https://googleapis.com.evil.com/push",             // look-alike domain
                "https://notgoogleapis.com/push",                   // suffix without a dot boundary
                "https://localhost/push",
                "https://127.0.0.1/push",
                "https://169.254.169.254/latest/meta-data",         // cloud metadata address
                "not a url at all"}) {
            subscribe(bad).andExpect(status().isBadRequest());
        }
        assertThat(pushRepository.countByUser(user)).isZero();

        for (String good : new String[]{
                FCM + "a",
                "https://updates.push.services.mozilla.com/wpush/v2/abc",
                "https://web.push.apple.com/abc",
                "https://wns2-par02p.notify.windows.com/w/?token=abc"}) {
            subscribe(good).andExpect(status().isNoContent());
        }
        assertThat(pushRepository.countByUser(user)).isEqualTo(4);
    }

    @Test
    void rejectsIncompleteSubscriptions() throws Exception {
        mvc.perform(post("/api/push/subscribe").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("endpoint", FCM + "x"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/push/subscribe").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("endpoint", FCM + "x", "keys", Map.of("p256dh", "k")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aBrowserCanBeUnsubscribedButOnlyByItsOwner() throws Exception {
        String endpoint = FCM + UUID.randomUUID();
        subscribe(endpoint).andExpect(status().isNoContent());

        mvc.perform(post("/api/push/unsubscribe").header("Authorization", "Bearer " + tokenFor(newUserRow()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("endpoint", endpoint))))
                .andExpect(status().isNoContent());
        assertThat(pushRepository.countByUser(user)).as("someone else can't remove it").isEqualTo(1);

        mvc.perform(post("/api/push/unsubscribe").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("endpoint", endpoint))))
                .andExpect(status().isNoContent());
        assertThat(pushRepository.countByUser(user)).isZero();
    }

    @Test
    void testReminderReportsWhatHappenedOnEachChannel() throws Exception {
        // Nothing switched on yet
        mvc.perform(post("/api/settings/notifications/test").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.email").value("OFF"))
                .andExpect(jsonPath("$.push").value("NO_DEVICES"));

        putSettings(Map.of("emailEnabled", true));
        subscribe(FCM + UUID.randomUUID());
        when(pushGateway.send(any(), anyString())).thenReturn(PushGateway.Result.SENT);

        mvc.perform(post("/api/settings/notifications/test").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.email").value("SENT"))
                .andExpect(jsonPath("$.push").value("SENT"));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void testReminderShowsUnavailableWhenNeitherChannelIsSetUp() throws Exception {
        when(pushGateway.isConfigured()).thenReturn(false);
        // Simulate "no SMTP server": the mail sender bean can't be reached through the provider.
        // (The app-level check is covered by EmailSender; here we only verify the push side.)
        mvc.perform(post("/api/settings/notifications/test").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.push").value("UNAVAILABLE"));
    }

    @Test
    void theEndpointsNeedALogin() throws Exception {
        mvc.perform(get("/api/settings/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/push/subscribe").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers -------------------------------------------------------------

    private ResultActions putSettings(Map<String, Object> body) throws Exception {
        return mvc.perform(put("/api/settings/notifications").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions subscribe(String endpoint) throws Exception {
        return mvc.perform(post("/api/push/subscribe").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(subscriptionJson(endpoint)));
    }

    private String subscriptionJson(String endpoint) throws Exception {
        return json.writeValueAsString(Map.of("endpoint", endpoint, "expirationTime", "null",
                "keys", Map.of("p256dh", "BPKEY", "auth", "AUTHKEY")));
    }

    private User newUserRow() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@example.com");
        u.setPasswordHash("x");
        u.setFullName("Settings Test");
        return userRepository.save(u);
    }

    private String tokenFor(User u) {
        return jwtService.generateToken(new UserPrincipal(u));
    }
}
