package com.odin.notification.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.odin.notification.constants.ApplicationConstants;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;

/**
 * Firebase Cloud Messaging Utility Component
 * Provides methods for sending push notifications via FCM
 */
@Slf4j
@Component
public class FcmUtil {

    private final FirebaseMessaging firebaseMessaging;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── APNs VoIP push configuration ──────────────────────────────────────────
    // Set these via application.properties or config server.
    // apns.key.path  — absolute path to the .p8 AuthKey file from Apple Developer portal
    // apns.key.id    — 10-character Key ID shown in Apple Developer portal
    // apns.team.id   — 10-character Team ID from Apple Developer portal
    // apns.bundle.id — iOS app bundle identifier (e.g. com.odin.messenger)
    // apns.production — true for production APNs, false for sandbox

    @Value("${apns.key.path:}")
    private String apnsKeyPath;

    @Value("${apns.key.id:}")
    private String apnsKeyId;

    @Value("${apns.team.id:}")
    private String apnsTeamId;

    @Value("${apns.bundle.id:com.odin.messenger}")
    private String apnsBundleId;

    @Value("${apns.production:false}")
    private boolean apnsProduction;

    // APNs JWT is valid for up to 1 hour; cache to avoid signing on every call.
    private volatile String cachedApnsJwt = null;
    private volatile long apnsJwtIssuedAtSeconds = 0;
    private volatile PrivateKey apnsPrivateKey = null;

    public FcmUtil(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    /**
     * Send push notification via FCM
     * 
     * @param token    FCM token of the device
     * @param title    Notification title
     * @param body     Notification body/message
     * @param dataMap  Additional data to send with the notification
     * @return Message ID if successful, null otherwise
     */
    public String sendPushNotification(String token, String title, String body, Map<String, String> dataMap) {
        log.debug("Preparing to send FCM push notification to token: {}", token);

        try {
            // Build data map with default click action
            Map<String, String> data = new HashMap<>();
            if (dataMap != null) {
                data.putAll(dataMap);
            }
            data.put(ApplicationConstants.FCM_CLICK_ACTION, ApplicationConstants.FCM_CLICK_ACTION);

            // Build notification
            Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            // Build Android-specific configuration
            AndroidNotification androidNotification = AndroidNotification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .setChannelId(ApplicationConstants.FCM_NOTIFICATION_CHANNEL_ID)
                    .setClickAction(ApplicationConstants.FCM_CLICK_ACTION)
                    .build();

            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setNotification(androidNotification)
                    .build();

            // Build complete message
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(notification)
                    .putAllData(data)
                    .setAndroidConfig(androidConfig)
                    .build();

            // Log sanitized payload for security
            log.info("FINAL FCM PAYLOAD for token {}: {}", token, sanitizeDataMap(data));

            // Send message
            String messageId = firebaseMessaging.send(message);

            log.info(ApplicationConstants.LOG_FCM_PUSH_SENT_SUCCESSFULLY, messageId);
            return messageId;

        } catch (FirebaseMessagingException e) {
            log.error(ApplicationConstants.LOG_FCM_PUSH_FAILED, e.getMessage(), e);
            log.error("Error details - Code: {}, Message: {}", e.getMessagingErrorCode(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error while sending FCM notification: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Send push notification with custom click action
     * 
     * @param token       FCM token of the device
     * @param title       Notification title
     * @param body        Notification body/message
     * @param dataMap     Additional data to send with the notification
     * @param clickAction Custom click action
     * @return Message ID if successful, null otherwise
     */
    public String sendCustomPushNotification(String token, String title, String body, 
                                             Map<String, String> dataMap, String clickAction) {
        log.debug("Preparing to send custom FCM push notification to token: {}", token);

        try {
            // Build data map with custom click action
            Map<String, String> data = new HashMap<>();
            if (dataMap != null) {
                data.putAll(dataMap);
            }
            data.put("click_action", clickAction);

            // Build notification
            Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            // Build Android-specific configuration with custom click action
            AndroidNotification androidNotification = AndroidNotification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .setChannelId(ApplicationConstants.FCM_NOTIFICATION_CHANNEL_ID)
                    .setClickAction(clickAction)
                    .build();

            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setNotification(androidNotification)
                    .build();

            // Build complete message
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(notification)
                    .putAllData(data)
                    .setAndroidConfig(androidConfig)
                    .build();

            // Log sanitized payload for security
            log.info("FINAL CUSTOM FCM PAYLOAD for token {}: {}", token, sanitizeDataMap(data));

            // Send message
            String messageId = firebaseMessaging.send(message);

            log.info(ApplicationConstants.LOG_FCM_PUSH_SENT_SUCCESSFULLY, messageId);
            log.debug("Custom push notification sent successfully with message ID: {}", messageId);

            return messageId;

        } catch (FirebaseMessagingException e) {
            log.error(ApplicationConstants.LOG_FCM_PUSH_FAILED, e.getMessage(), e);
            log.error("Error details - Code: {}, Message: {}", e.getMessagingErrorCode(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error while sending custom FCM notification: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Send data-only push notification (no notification object)
     * This allows the mobile app to handle the payload in the background
     * even when the device is locked.
     * 
     * @param token    FCM token of the device
     * @param dataMap  Data payload to send
     * @param isSilent Whether this is a silent background notification (STATUS_UPDATE) 
     *                 or an alert notification (MESSAGE)
     * @return Message ID if successful, null otherwise
     */
    public String sendDataOnlyPushNotification(String token, Map<String, String> dataMap, boolean isSilent)
            throws FirebaseMessagingException {
        log.debug("Preparing to send data-only FCM push notification to token: {}, isSilent: {}", token, isSilent);

        // Build Android-specific configuration with HIGH priority to wake the device
        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build();

        // Build APNS-specific configuration
        // isSilent = false (MESSAGE): background type, priority 10, content-available 1 only — no aps.alert
        //   iOS silently wakes Flutter handler → Flutter shows ONE rich local notification after E2EE decrypt
        // isSilent = true (STATUS_UPDATE): background type, priority 5, content-available 1 only
        ApnsConfig apnsConfig;
        if (isSilent) {
            apnsConfig = ApnsConfig.builder()
                    .putHeader("apns-push-type", "background")
                    .putHeader("apns-priority", "5")
                    .setAps(Aps.builder()
                            .setContentAvailable(true)
                            .build())
                    .build();
        } else {
            // For non-silent chat messages:
            //
            // WHY NO aps.alert:
            // iOS renders aps.alert as a native system banner BEFORE calling the Flutter background
            // handler. AppDelegate.willPresent can suppress this in foreground (via completionHandler([]))
            // but has NO control when the app is backgrounded or the device is locked — iOS shows the
            // native banner unconditionally. This caused a DOUBLE notification:
            //   1. Native APNs banner ("New Message / You have a new message") from aps.alert
            //   2. Flutter local notification (rich content) from _fln.show() after E2EE decryption
            //
            // FIX: Remove aps.alert + sound entirely. Use background push-type + content-available:1
            // so iOS silently wakes the Flutter background handler (didReceiveRemoteNotification:),
            // which then shows exactly ONE rich local notification via FlutterLocalNotificationsPlugin.
            //
            // WHY apns-priority:10 with background type:
            // Priority 10 ensures high-priority delivery even for background pushes; Apple allows
            // this but may throttle on low-battery devices. Acceptable trade-off vs. double banners.
            //
            // ANDROID IMPACT: Zero. This entire block is inside ApnsConfig which FCM routes to iOS
            // only. AndroidConfig (above) is unchanged and uses its own HIGH priority path.
            apnsConfig = ApnsConfig.builder()
                    .putHeader("apns-push-type", "background")
                    .putHeader("apns-priority", "10")
                    .setAps(Aps.builder()
                            .setContentAvailable(true)  // Wakes Flutter background handler (97% confidence fix)
                            .build())
                    .build();
            log.info("[APNs-BackgroundWakeup] contentAvailable=true, no aps.alert (single Flutter local notification will display rich content)");
        }

        Map<String, String> sanitizedData = sanitizeReservedKeys(dataMap);

        // Build message without notification object
        Message message = Message.builder()
                .setToken(token)
                .putAllData(sanitizedData)
                .setAndroidConfig(androidConfig)
                .setApnsConfig(apnsConfig)
                .build();

        // Log sanitized payload for security
        log.info("FINAL DATA-ONLY FCM PAYLOAD (isSilent: {}) for token {}: {}",
                isSilent, token, sanitizeDataMap(sanitizedData));

        // Send message — let exceptions propagate for caller retry handling
        String messageId = firebaseMessaging.send(message);

        log.info(ApplicationConstants.LOG_FCM_PUSH_SENT_SUCCESSFULLY, messageId);
        return messageId;
    }

    /**
     * Send push notification with minimal data (only title and body)
     * 
     * @param token FCM token of the device
     * @param title Notification title
     * @param body  Notification body/message
     * @return Message ID if successful, null otherwise
     */
    public String sendSimplePushNotification(String token, String title, String body) {
        log.debug("Sending simple push notification to token: {}", token);
        return sendPushNotification(token, title, body, null);
    }

    /**
     * Sanitize data map for logging by masking cryptographic material.
     * 
     * @param dataMap The original data map
     * @return A copy of the map with sensitive fields masked
     */
    private Map<String, String> sanitizeDataMap(Map<String, String> dataMap) {
        if (dataMap == null) return null;
        Map<String, String> sanitized = new HashMap<>(dataMap);
        
        // Define fields that should never be logged in plaintext
        String[] sensitiveFields = {"ciphertext", "iv", "tag", "senderPublicKey", "senderKeyVersion"};
        
        for (String field : sensitiveFields) {
            if (sanitized.containsKey(field)) {
                sanitized.put(field, "[MASKED]");
            }
        }
        
        return sanitized;
    }

    private Map<String, String> sanitizeReservedKeys(Map<String, String> dataMap) {
        Map<String, String> sanitized = new HashMap<>();
        if (dataMap != null) {
            sanitized.putAll(dataMap);
        }

        if (sanitized.containsKey("from")) {
            sanitized.put("callerId", sanitized.get("from"));
            sanitized.remove("from");
        }

        sanitized.keySet().removeIf(key -> key.startsWith("google.") || key.startsWith("gcm."));

        return sanitized;
    }

    /**
     * Validate FCM token format
     * 
     * @param token FCM token to validate
     * @return true if token is valid, false otherwise
     */
    public boolean isValidFcmToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.warn("FCM token is null or empty");
            return false;
        }

        // Basic validation: FCM tokens are typically long strings
        if (token.length() < 100) {
            log.warn("FCM token appears to be too short, may be invalid");
            return false;
        }

        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // APNs VoIP Push (Phase 3 — iOS CallKit wake-from-killed)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends an APNs VoIP push directly to Apple's servers using HTTP/2.
     * This bypasses FCM entirely and wakes the iOS app even when killed, which
     * is required for CallKit to show the native incoming-call UI on lock screen.
     *
     * <p>Apple rules enforced here:
     * <ul>
     *   <li>{@code apns-push-type: voip} — required for PushKit delivery</li>
     *   <li>{@code apns-topic: {bundleId}.voip} — the VoIP topic suffix is mandatory</li>
     *   <li>{@code apns-priority: 10} — VoIP pushes must be high-priority</li>
     *   <li>APNs JWT signed with ES256, cached for up to 50 minutes (Apple allow 60 max)</li>
     * </ul>
     *
     * @param voipToken   PushKit VoIP APNs token (hex string, registered by Flutter app)
     * @param callPayload call fields to forward (sessionId, callerName, callType, etc.)
     * @return HTTP status code from APNs (200 = success), or -1 on error
     */
    public int sendVoipApnsPush(String voipToken, Map<String, String> callPayload) {
        if (!isApnsConfigured()) {
            log.warn("[VoIP-APNs] SKIPPED — APNs not configured (apns.key.path/id/team.id missing). "
                    + "Set these properties to enable iOS CallKit wake-from-killed.");
            return -1;
        }
        if (voipToken == null || voipToken.isBlank()) {
            log.warn("[VoIP-APNs] SKIPPED — voipToken is null or blank");
            return -1;
        }

        try {
            // Build JSON payload — all call fields are nested under the root dict.
            // AppDelegate.didReceiveIncomingPushWith reads dictionaryPayload directly.
            Map<String, Object> root = new HashMap<>();
            if (callPayload != null) {
                root.putAll(callPayload);
            }
            // Ensure mandatory fields present for AppDelegate CallKit routing
            root.put("type", "CALL_INVITE");
            String jsonBody = objectMapper.writeValueAsString(root);

            // Build APNs JWT
            String jwt = buildApnsJwt();

            // APNs endpoint
            String apnsHost = apnsProduction
                    ? "https://api.push.apple.com"
                    : "https://api.sandbox.push.apple.com";
            String url = apnsHost + "/3/device/" + voipToken;

            log.info("[VoIP-APNs] Sending VoIP push to APNs for voipToken={} (prod={}) payload={}",
                    voipToken.substring(0, Math.min(8, voipToken.length())) + "...",
                    apnsProduction, sanitizeDataMap(callPayload));

            HttpClient httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("content-type", "application/json")
                    .header("apns-push-type", "voip")
                    .header("apns-topic", apnsBundleId + ".voip")
                    .header("apns-priority", "10")
                    .header("authorization", "bearer " + jwt)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 200) {
                log.info("[VoIP-APNs] ✅ VoIP push delivered successfully — status=200");
            } else {
                log.error("[VoIP-APNs] ❌ VoIP push failed — status={} body={}", status, response.body());
            }
            return status;

        } catch (Exception e) {
            log.error("[VoIP-APNs] ❌ Exception sending VoIP push: {}", e.getMessage(), e);
            return -1;
        }
    }

    /**
     * Returns true if all required APNs config properties are present.
     */
    private boolean isApnsConfigured() {
        return apnsKeyPath != null && !apnsKeyPath.isBlank()
                && apnsKeyId != null && !apnsKeyId.isBlank()
                && apnsTeamId != null && !apnsTeamId.isBlank();
    }

    /**
     * Builds and caches the APNs provider JWT (ES256, valid up to 50 minutes).
     * Apple allows up to 60 min; we refresh at 50 min for a safety margin.
     */
    private synchronized String buildApnsJwt() throws Exception {
        long nowSeconds = System.currentTimeMillis() / 1000;
        // Refresh if no cached token or if it's older than 50 minutes
        if (cachedApnsJwt == null || (nowSeconds - apnsJwtIssuedAtSeconds) > 3000) {
            log.debug("[VoIP-APNs] Building new APNs JWT (teamId={}, keyId={})", apnsTeamId, apnsKeyId);
            PrivateKey pk = loadApnsPrivateKey();
            cachedApnsJwt = Jwts.builder()
                    .setHeaderParam("kid", apnsKeyId)
                    .setIssuer(apnsTeamId)
                    .setIssuedAt(new Date(nowSeconds * 1000))
                    .signWith(pk, SignatureAlgorithm.ES256)
                    .compact();
            apnsJwtIssuedAtSeconds = nowSeconds;
            log.debug("[VoIP-APNs] APNs JWT built and cached — issuedAt={}", apnsJwtIssuedAtSeconds);
        }
        return cachedApnsJwt;
    }

    /**
     * Loads and caches the ECDSA private key from the .p8 PEM file at {@code apnsKeyPath}.
     */
    private synchronized PrivateKey loadApnsPrivateKey() throws Exception {
        if (apnsPrivateKey != null) return apnsPrivateKey;
        java.nio.file.Path keyPath = Paths.get(apnsKeyPath).toAbsolutePath();
        log.info("[VoIP-APNs] Loading APNs P8 private key from absolute path: {}", keyPath);
        if (!java.nio.file.Files.exists(keyPath)) {
            log.error("[VoIP-APNs] ❌ P8 key file NOT FOUND at: {}. Check apns.key.path in application.properties.", keyPath);
            throw new java.io.FileNotFoundException("APNs P8 key not found: " + keyPath);
        }
        byte[] keyFileBytes = Files.readAllBytes(keyPath);
        String pem = new String(keyFileBytes)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        apnsPrivateKey = keyFactory.generatePrivate(keySpec);
        log.info("[VoIP-APNs] ✅ APNs P8 private key loaded successfully from {}", keyPath);
        return apnsPrivateKey;
    }
}
