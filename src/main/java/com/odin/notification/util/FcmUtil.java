package com.odin.notification.util;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
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

            // Send message
            String messageId = firebaseMessaging.send(message);

            log.info(ApplicationConstants.LOG_FCM_PUSH_SENT_SUCCESSFULLY, messageId);
            log.debug("Push notification sent successfully with message ID: {}", messageId);

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
