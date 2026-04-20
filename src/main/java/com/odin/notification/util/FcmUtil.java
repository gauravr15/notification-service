package com.odin.notification.util;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

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

import lombok.extern.slf4j.Slf4j;

/**
 * Firebase Cloud Messaging Utility Component
 * Provides methods for sending push notifications via FCM
 */
@Slf4j
@Component
public class FcmUtil {

    private final FirebaseMessaging firebaseMessaging;

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
        // isSilent = false (MESSAGE): alert type, priority 10, content-available 1, with alert body for iOS
        // isSilent = true (STATUS_UPDATE): background type, priority 5, content-available 1
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
            // For non-silent messages (chat), include an APNs alert with content-available:1
            // so iOS delivers the message as a visible alert AND wakes the Flutter background
            // handler to trigger E2EE decryption and local notification display.
            //
            // Per iOS/APNs spec:
            // - content-available:1 enables didReceiveRemoteNotification:fetchCompletionHandler:
            // - apns-push-type:alert shows a user-visible banner
            // - sound:default provides audio feedback
            //
            // CRITICAL FIX: Without content-available:1, iOS treats the push as display-only
            // and never calls the background handler. Flutter code never runs, no E2EE decryption,
            // no local notification. This is why messages were lost entirely on iOS.
            //
            // Risk: Apple may demote alert+content-available to silent behavior (tested in dev).
            // Solution: Monitor logs for banner suppression; fall back to silent if needed.
            String alertTitle = dataMap.getOrDefault("title", "New Message");
            String alertBody = dataMap.getOrDefault("body", "You have a new message");
            apnsConfig = ApnsConfig.builder()
                    .putHeader("apns-push-type", "alert")
                    .putHeader("apns-priority", "10")
                    .setAps(Aps.builder()
                            .setContentAvailable(true)  // ← FIX #1: Enable iOS background handler wakeup (97% confidence)
                            .setAlert(ApsAlert.builder()
                                    .setTitle(alertTitle)
                                    .setBody(alertBody)
                                    .build())
                            .setSound("default")
                            .build())
                    .build();
            log.info("[APNs-AlertWithContentAvailable] title={}, body={}, contentAvailable=true (iOS background handler enabled)", 
                    alertTitle, alertBody);
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
}
