package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.PushSubscription;
import com.expensetracker.expensetracker.service.delivery.PushGateway;
import com.expensetracker.expensetracker.service.delivery.WebPushGateway;
import com.sun.net.httpserver.HttpServer;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real web-push code (payload encryption and VAPID signing) against a local server standing in
 * for a browser's push service, using real generated keys. It can't prove Google or Apple accept the
 * message, but it does prove we produce a well-formed, encrypted, signed push request.
 */
class WebPushGatewayTest {

    HttpServer server;
    final AtomicInteger status = new AtomicInteger(201);
    final AtomicReference<com.sun.net.httpserver.Headers> lastHeaders = new AtomicReference<>();
    final AtomicReference<byte[]> lastBody = new AtomicReference<>();

    String vapidPublic;
    String vapidPrivate;

    @BeforeEach
    void startFakePushService() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPair vapid = newKeyPair();
        vapidPublic = b64(((ECPublicKey) vapid.getPublic()).getQ().getEncoded(false));
        vapidPrivate = b64(unsigned32(((ECPrivateKey) vapid.getPrivate()).getD().toByteArray()));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push", exchange -> {
            lastHeaders.set(exchange.getRequestHeaders());
            lastBody.set(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsAnEncryptedSignedRequestToTheDevicesPushAddress() throws Exception {
        WebPushGateway gateway = new WebPushGateway(vapidPublic, vapidPrivate, "mailto:test@example.com");
        String payload = "{\"title\":\"Netflix due tomorrow\"}";

        PushGateway.Result result = gateway.send(subscription(), payload);

        assertThat(gateway.isConfigured()).isTrue();
        assertThat(gateway.publicKey()).isEqualTo(vapidPublic);
        assertThat(result).isEqualTo(PushGateway.Result.SENT);
        assertThat(lastHeaders.get().getFirst("Authorization")).startsWith("vapid t=").contains("k=" + vapidPublic);
        assertThat(lastHeaders.get().getFirst("Content-Encoding")).isEqualTo("aes128gcm");
        assertThat(lastHeaders.get().getFirst("TTL")).isNotBlank();
        byte[] body = lastBody.get();
        assertThat(new String(body, java.nio.charset.StandardCharsets.ISO_8859_1))
                .as("the body is encrypted, not the plain JSON")
                .doesNotContain("Netflix due tomorrow");
        // RFC 8188 header: 16-byte salt, 4-byte record size, 1-byte key id length (65 for an EC point), then the key.
        assertThat(body.length).isGreaterThan(16 + 4 + 1 + 65);
        assertThat(body[20] & 0xff).as("key id length").isEqualTo(65);
        assertThat(body[21] & 0xff).as("uncompressed EC point marker").isEqualTo(0x04);
    }

    @Test
    void reportsAnExpiredSubscriptionAsGone() throws Exception {
        WebPushGateway gateway = new WebPushGateway(vapidPublic, vapidPrivate, "mailto:test@example.com");

        status.set(410);
        assertThat(gateway.send(subscription(), "{}")).isEqualTo(PushGateway.Result.GONE);
        status.set(404);
        assertThat(gateway.send(subscription(), "{}")).isEqualTo(PushGateway.Result.GONE);
    }

    @Test
    void reportsServerErrorsAsATemporaryFailure() throws Exception {
        WebPushGateway gateway = new WebPushGateway(vapidPublic, vapidPrivate, "mailto:test@example.com");

        status.set(500);
        assertThat(gateway.send(subscription(), "{}")).isEqualTo(PushGateway.Result.FAILED);
    }

    @Test
    void anUnreachableServiceIsAFailureNotACrash() throws Exception {
        WebPushGateway gateway = new WebPushGateway(vapidPublic, vapidPrivate, "mailto:test@example.com");
        PushSubscription dead = subscription();
        dead.setEndpoint("http://127.0.0.1:1/push/nothing-listens-here");

        assertThat(gateway.send(dead, "{}")).isEqualTo(PushGateway.Result.FAILED);
    }

    @Test
    void isSwitchedOffWithoutKeysOrWithBrokenKeys() throws Exception {
        assertThat(new WebPushGateway("", "", "mailto:x@example.com").isConfigured()).isFalse();
        assertThat(new WebPushGateway("", "", "mailto:x@example.com").publicKey()).isNull();
        assertThat(new WebPushGateway("not-a-key", "also-not", "mailto:x@example.com").isConfigured()).isFalse();
        assertThat(new WebPushGateway("", "", "mailto:x@example.com").send(subscription(), "{}"))
                .isEqualTo(PushGateway.Result.FAILED);
    }

    // --- helpers -------------------------------------------------------------

    /** A pretend browser: its own key pair and auth secret, exactly what a real PushSubscription carries. */
    private PushSubscription subscription() throws Exception {
        KeyPair browser = newKeyPair();
        byte[] authSecret = new byte[16];
        new SecureRandom().nextBytes(authSecret);

        PushSubscription subscription = new PushSubscription();
        subscription.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/push/device-1");
        subscription.setP256dh(b64(((ECPublicKey) browser.getPublic()).getQ().getEncoded(false)));
        subscription.setAuth(b64(authSecret));
        return subscription;
    }

    private KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", "BC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** BigInteger.toByteArray() may add a leading zero or drop leading zeros; VAPID wants exactly 32 bytes. */
    private byte[] unsigned32(byte[] value) {
        if (value.length == 32) return value;
        byte[] out = new byte[32];
        if (value.length > 32) {
            System.arraycopy(value, value.length - 32, out, 0, 32);
        } else {
            System.arraycopy(value, 0, out, 32 - value.length, value.length);
        }
        return out;
    }
}
