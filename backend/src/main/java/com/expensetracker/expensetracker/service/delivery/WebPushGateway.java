package com.expensetracker.expensetracker.service.delivery;

import com.expensetracker.expensetracker.model.PushSubscription;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Security;

/**
 * Real Web Push, signed with the app's VAPID keys.
 *
 * Generate a key pair once with:  npx web-push generate-vapid-keys
 * then set app.push.public-key, app.push.private-key and app.push.subject (a mailto: address).
 */
@Slf4j
@Component
public class WebPushGateway implements PushGateway {

    private final String publicKey;
    private final PushService pushService;

    public WebPushGateway(
            @Value("${app.push.public-key:}") String publicKey,
            @Value("${app.push.private-key:}") String privateKey,
            @Value("${app.push.subject:mailto:admin@cashmatrix.local}") String subject) {
        PushService service = null;
        if (!publicKey.isBlank() && !privateKey.isBlank()) {
            try {
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(new BouncyCastleProvider());
                }
                service = new PushService(publicKey, privateKey, subject);
            } catch (Exception ex) {
                log.error("Push notifications are disabled: the VAPID keys could not be loaded", ex);
            }
        }
        this.pushService = service;
        this.publicKey = service != null ? publicKey : null;
    }

    @Override
    public boolean isConfigured() {
        return pushService != null;
    }

    @Override
    public String publicKey() {
        return publicKey;
    }

    @Override
    public Result send(PushSubscription subscription, String jsonPayload) {
        if (pushService == null) {
            return Result.FAILED;
        }
        try {
            // aes128gcm is the current standard (RFC 8291) and the only one Apple's push service accepts;
            // the library's default is the older "aesgcm" draft.
            HttpResponse response = pushService.send(new nl.martijndwars.webpush.Notification(
                    subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuth(),
                    jsonPayload.getBytes(StandardCharsets.UTF_8)), Encoding.AES128GCM);
            int status = response.getStatusLine().getStatusCode();
            if (status >= 200 && status < 300) return Result.SENT;
            if (status == 404 || status == 410) return Result.GONE;
            log.warn("Push service answered {} for subscription {}", status, subscription.getId());
            return Result.FAILED;
        } catch (Exception ex) {
            log.warn("Push to subscription {} failed: {}", subscription.getId(), ex.toString());
            return Result.FAILED;
        }
    }
}
