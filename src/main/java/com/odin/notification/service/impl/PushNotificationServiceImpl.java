package com.odin.notification.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.odin.notification.constants.ApplicationConstants;
import com.odin.notification.dto.NotificationDTO;
import com.odin.notification.entity.NotificationToken;
import com.odin.notification.enums.NotificationChannel;
import com.odin.notification.repo.NotificationTokenRepository;
import com.odin.notification.service.PushNotificationService;
import com.odin.notification.util.FcmUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Push Notification Service Implementation
 * Handles the business logic for processing and sending push notifications
 */
@Slf4j
@Service
public class PushNotificationServiceImpl implements PushNotificationService {

    private final FcmUtil fcmUtil;
    private final NotificationTokenRepository notificationTokenRepository;

    public PushNotificationServiceImpl(FcmUtil fcmUtil, NotificationTokenRepository notificationTokenRepository) {
        this.fcmUtil = fcmUtil;
        this.notificationTokenRepository = notificationTokenRepository;
    }

    @Override
    public void processPushNotification(NotificationDTO notificationDTO) {
        log.info(ApplicationConstants.LOG_NOTIFICATION_PROCESSING_STARTED);
        log.info(ApplicationConstants.LOG_UNDELIVERED_NOTIFICATION_RECEIVED,
                notificationDTO.getCustomerId(),
                notificationDTO.getNotificationId(),
                notificationDTO.getChannel());

        try {
            // Validate notification data
            if (!isValidNotification(notificationDTO)) {
                log.error("Invalid notification data received: {}", notificationDTO);
                return;
            }

            // Route notification based on channel
            routeNotificationByChannel(notificationDTO);

            log.info(ApplicationConstants.LOG_NOTIFICATION_PROCESSING_COMPLETED);

        } catch (Exception e) {
            log.error(ApplicationConstants.LOG_ERROR_PROCESSING_NOTIFICATION,
                    notificationDTO.getCustomerId(),
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Route notification to appropriate handler based on channel
     * 
     * @param notificationDTO The notification to process
     */
    private void routeNotificationByChannel(NotificationDTO notificationDTO) {
        log.info(ApplicationConstants.LOG_PROCESSING_NOTIFICATION,
                notificationDTO.getCustomerId(),
                notificationDTO.getNotificationId(),
                notificationDTO.getChannel());

        NotificationChannel channel = notificationDTO.getChannel();

        switch (channel) {
            case INAPP:
                handleInAppNotification(notificationDTO);
                break;
            case EMAIL:
                handleEmailNotification(notificationDTO);
                break;
            case SMS:
                handleSmsNotification(notificationDTO);
                break;
            default:
                log.warn(ApplicationConstants.LOG_UNSUPPORTED_CHANNEL, channel);
        }
    }

    /**
     * Handle INAPP notifications (Push notifications)
     * 
     * @param notificationDTO The notification data
     */
    private void handleInAppNotification(NotificationDTO notificationDTO) {
        log.debug("Processing INAPP notification for customerId: {}", notificationDTO.getCustomerId());
        if (notificationDTO.getMap() != null) {
            log.info("[NOTIFY-CONSUMER-DEBUG] Received notification map from Kafka: {}", notificationDTO.getMap());
            log.info("[NOTIFY-CONSUMER-DEBUG] conversationId={}, groupId={}", notificationDTO.getMap().get("conversationId"), notificationDTO.getMap().get("groupId"));
            log.info(
              "[KAFKA-ENTRY] dtoHash={} mapHash={} mapKeys={}",
              System.identityHashCode(notificationDTO),
              System.identityHashCode(notificationDTO.getMap()),
              notificationDTO.getMap().keySet()
            );
        } else {
            log.warn("[NOTIFY-CONSUMER-DEBUG] NotificationDTO map is null for customerId={}", notificationDTO.getCustomerId());
        }

        // Check if this is a direct message (notificationId = 1)
        if (isDirectMessage(notificationDTO.getNotificationId())) {
            log.info(ApplicationConstants.LOG_NOTIFICATION_ID_IS_DIRECT,
                    notificationDTO.getNotificationId());

            // Extract message from map
            String message = extractMessageFromMap(notificationDTO.getMap());

            // If it's not encrypted, we require a message. If it is encrypted, message can be empty.
            if (!notificationDTO.isEncrypted() && (message == null || message.isEmpty())) {
                log.warn("No message found in notification map for non-encrypted message, customerId: {}",
                        notificationDTO.getCustomerId());
                return;
            }

            // Send push notification
            sendPushNotification(notificationDTO, message);

        } else {
            log.debug("Notification ID is not direct message. Would fetch template from database for ID: {}",
                    notificationDTO.getNotificationId());
            // TODO: Implement template fetching from database for other notification IDs
        }
    }

    /**
     * Handle EMAIL notifications
     * 
     * @param notificationDTO The notification data
     */
    private void handleEmailNotification(NotificationDTO notificationDTO) {
        log.debug("Processing EMAIL notification for customerId: {}", notificationDTO.getCustomerId());
        // TODO: Implement email sending logic
        log.info("Email notification handling not yet implemented for customerId: {}",
                notificationDTO.getCustomerId());
    }

    /**
     * Handle SMS notifications
     * 
     * @param notificationDTO The notification data
     */
    private void handleSmsNotification(NotificationDTO notificationDTO) {
        log.debug("Processing SMS notification for customerId: {}", notificationDTO.getCustomerId());
        // TODO: Implement SMS sending logic
        log.info("SMS notification handling not yet implemented for customerId: {}",
                notificationDTO.getCustomerId());
    }

    /**
     * Send push notification via FCM
     * Fetches the FCM token from database based on customerId
     * 
     * @param notificationDTO The notification data
     * @param message         The message to send
     */
    private void sendPushNotification(NotificationDTO notificationDTO, String message) {
        log.debug("Preparing to send push notification for customerId: {}",
                notificationDTO.getCustomerId());

        // Fetch FCM token from database based on customerId
        String fcmToken = fetchFcmTokenFromDatabase(notificationDTO.getCustomerId());

        if (fcmToken == null || fcmToken.isEmpty()) {
            log.error(ApplicationConstants.LOG_FCM_TOKEN_NOT_FOUND, notificationDTO.getCustomerId());
            return;
        }

        log.info(ApplicationConstants.LOG_SENDING_FCM_PUSH_NOTIFICATION,
                fcmToken,
                message);

        // Build data map for FCM
        Map<String, String> fcmDataMap = buildFcmDataMap(notificationDTO);
        if (fcmDataMap == null) {
            log.warn("[FCM] Skipping push for customerId={} due to missing conversationId", notificationDTO.getCustomerId());
            return;
        }

        // Send data-only notification (no notification object)
        // This allows the app to render the notification with sound and deep-link handling
        // For messages, we use isSilent = false to set 'alert' type for APNs
        String messageId = fcmUtil.sendDataOnlyPushNotification(
                fcmToken,
                fcmDataMap,
                false
        );

        if (messageId != null) {
            log.info("Push notification sent successfully for customerId: {}, messageId: {}",
                    notificationDTO.getCustomerId(),
                    messageId);
        } else {
            log.error("Failed to send push notification for customerId: {}",
                    notificationDTO.getCustomerId());
        }
    }

    /**
     * Build FCM data map from notification DTO
     * 
     * @param notificationDTO The notification data
     * @return Map with FCM data
     */
    private Map<String, String> buildFcmDataMap(NotificationDTO notificationDTO) {
                log.info(
                  "[FCM-BUILD] dtoHash={} mapHash={} mapKeys={}",
                  System.identityHashCode(notificationDTO),
                  notificationDTO.getMap() != null
                      ? System.identityHashCode(notificationDTO.getMap())
                      : null,
                  notificationDTO.getMap() != null
                      ? notificationDTO.getMap().keySet()
                      : null
                );
        Map<String, String> fcmData = new HashMap<>();

        Map<String, Object> map = notificationDTO.getMap();
        String conversationId = notificationDTO.getConversationId();
        if (conversationId == null || conversationId.isEmpty()) {
            log.warn("[FCM-BUILD] Missing conversationId for customerId={}", notificationDTO.getCustomerId());
            return null;
        }

        String groupId =
            map != null && map.get("groupId") != null
                ? String.valueOf(map.get("groupId"))
                : null;

        Boolean isGroup =
            map != null && map.get("isGroup") != null
                ? Boolean.valueOf(String.valueOf(map.get("isGroup")))
                : conversationId.startsWith("group:");

        String groupName =
            map != null && map.get("groupName") != null
                ? String.valueOf(map.get("groupName"))
                : null;

        // Add all data from notification map, converting values to String
        if (map != null) {
            map.forEach((key, value) -> {
                if (value != null) {
                    if (value instanceof java.util.List) {
                        String joined = ((java.util.List<?>) value).stream()
                                .map(String::valueOf)
                                .collect(java.util.stream.Collectors.joining(","));
                        fcmData.put(key, joined);
                    } else {
                        fcmData.put(key, String.valueOf(value));
                    }
                }
            });
        }

        // Use only resolved values for FCM payload
        fcmData.put("conversationId", conversationId);
        if (groupId != null) {
            fcmData.put("groupId", groupId);
        }
        fcmData.put("isGroup", String.valueOf(isGroup));
        if (groupName != null) {
            fcmData.put("groupName", groupName);
        }

        log.info(
          "[FCM-BUILD] conversationId={} groupId={} isGroup={} groupName={}",
          conversationId, groupId, isGroup, groupName
        );

        // Add mandatory fields for data-only message rendering
        fcmData.put("type", ApplicationConstants.FCM_NOTIFICATION_TYPE_MESSAGE);
        fcmData.put("title", notificationDTO.getSenderName() != null ? notificationDTO.getSenderName() : "Odin Messenger");

        // Handle body based strictly on payload encryption state (independent of any config)
        if (notificationDTO.isEncrypted()) {
            log.debug("Encrypted message detected for customerId: {}. Skipping body entirely.", notificationDTO.getCustomerId());
            fcmData.remove("body");
            fcmData.remove("message");
            fcmData.remove("sampleMessage");
        } else {
            fcmData.put("body", notificationDTO.getMessage() != null ? notificationDTO.getMessage() : "New Message");
        }

        // Add sender and receiver info explicitly if available
        if (notificationDTO.getSenderCustomerId() != null) {
            fcmData.put(ApplicationConstants.FCM_SENDER_CUSTOMER_ID_KEY, notificationDTO.getSenderCustomerId());
        }
        if (notificationDTO.getSenderMobile() != null) {
            fcmData.put(ApplicationConstants.FCM_SENDER_MOBILE_KEY, notificationDTO.getSenderMobile());
            fcmData.put("senderPhone", notificationDTO.getSenderMobile());
        }

        // Add notification metadata
        fcmData.put("customerId", String.valueOf(notificationDTO.getCustomerId()));
        fcmData.put("notificationId", String.valueOf(notificationDTO.getNotificationId()));
        fcmData.put("channel", notificationDTO.getChannel().toString());

        log.debug("Built FCM data-only map for MESSAGE with {} entries", fcmData.size());
        return fcmData;
    }

    /**
     * Extract message from notification map
     * 
     * @param dataMap The data map from notification
     * @return The message value or null if not found
     */
    private String extractMessageFromMap(Map<String, Object> dataMap) {
        if (dataMap == null || dataMap.isEmpty()) {
            log.warn("Notification map is null or empty");
            return null;
        }

        Object message = dataMap.get(ApplicationConstants.FCM_MESSAGE_KEY);
        if (message == null) {
            message = dataMap.get("sampleMessage");
        }
        log.debug("Extracted message from map: {}", message != null ? "Found" : "Not found");
        return message != null ? String.valueOf(message) : null;
    }

    /**
     * Check if notification is a direct message
     * 
     * @param notificationId The notification ID to check
     * @return true if it's a direct message, false otherwise
     */
    private boolean isDirectMessage(Long notificationId) {
        boolean isDirect = ApplicationConstants.NOTIFICATION_ID_DIRECT_MESSAGE.equals(notificationId);
        log.debug("Checking if notification is direct message: notificationId={}, isDirect={}",
                notificationId, isDirect);
        return isDirect;
    }

    /**
     * Validate notification data
     * 
     * @param notificationDTO The notification to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidNotification(NotificationDTO notificationDTO) {
        if (notificationDTO == null) {
            log.error("NotificationDTO is null");
            return false;
        }

        if (notificationDTO.getCustomerId() == null) {
            log.error("Customer ID is null in notification");
            return false;
        }

        if (notificationDTO.getNotificationId() == null) {
            log.error("Notification ID is null in notification");
            return false;
        }

        if (notificationDTO.getChannel() == null) {
            log.error("Notification channel is null");
            return false;
        }

        log.debug("Notification validation passed for customerId: {}",
                notificationDTO.getCustomerId());
        return true;
    }

    /**
     * Fetch FCM token from database for a given customer
     * 
     * @param customerId The customer ID
     * @return The FCM token if found, null otherwise
     */
    private String fetchFcmTokenFromDatabase(Long customerId) {
        log.debug(ApplicationConstants.LOG_FCM_TOKEN_FETCHING_FROM_DB, customerId);

        try {
            Optional<NotificationToken> notificationToken = notificationTokenRepository.findFirstByCustomerId(customerId);

            if (notificationToken.isPresent()) {
                String fcmToken = notificationToken.get().getFcmToken();
                log.debug(ApplicationConstants.LOG_FCM_TOKEN_FOUND_IN_DB, customerId);

                if (fcmToken != null && !fcmToken.isEmpty()) {
                    log.info(ApplicationConstants.LOG_FCM_TOKEN_RETRIEVED_SUCCESS, customerId);
                    return fcmToken;
                } else {
                    log.warn(ApplicationConstants.LOG_FCM_TOKEN_EMPTY_IN_DB, customerId);
                    return null;
                }
            } else {
                log.warn(ApplicationConstants.LOG_FCM_TOKEN_RECORD_NOT_FOUND, customerId);
                return null;
            }
        } catch (Exception e) {
            log.error(ApplicationConstants.LOG_FCM_TOKEN_DB_FETCH_ERROR, customerId, e.getMessage(), e);
            return null;
        }
    }
}
