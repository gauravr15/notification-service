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
import com.odin.notification.service.StatusUpdateService;
import com.odin.notification.util.FcmUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Status Update Notification Service Implementation
 * Handles the business logic for processing and sending status update push notifications
 */
@Slf4j
@Service
public class StatusUpdateServiceImpl implements StatusUpdateService {

    private final FcmUtil fcmUtil;
    private final NotificationTokenRepository notificationTokenRepository;

    public StatusUpdateServiceImpl(FcmUtil fcmUtil, NotificationTokenRepository notificationTokenRepository) {
        this.fcmUtil = fcmUtil;
        this.notificationTokenRepository = notificationTokenRepository;
    }

    @Override
    public void processStatusUpdateNotification(NotificationDTO notificationDTO) {
        log.info(ApplicationConstants.LOG_STATUS_UPDATE_PROCESSING_STARTED);
        log.info(ApplicationConstants.LOG_STATUS_UPDATE_NOTIFICATION_RECEIVED,
                notificationDTO.getCustomerId(),
                notificationDTO.getNotificationId(),
                notificationDTO.getChannel());

        try {
            // Validate notification data
            if (!isValidStatusUpdate(notificationDTO)) {
                log.error("Invalid status update notification data received: {}", notificationDTO);
                return;
            }

            // Route notification based on channel
            routeNotificationByChannel(notificationDTO);

            log.info(ApplicationConstants.LOG_STATUS_UPDATE_PROCESSING_COMPLETED);

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
     * @param notificationDTO The status update notification to process
     */
    private void routeNotificationByChannel(NotificationDTO notificationDTO) {
        NotificationChannel channel = notificationDTO.getChannel();

        switch (channel) {
            case INAPP:
                handleInAppStatusUpdate(notificationDTO);
                break;
            case EMAIL:
                handleEmailStatusUpdate(notificationDTO);
                break;
            case SMS:
                handleSmsStatusUpdate(notificationDTO);
                break;
            default:
                log.warn(ApplicationConstants.LOG_UNSUPPORTED_CHANNEL, channel);
        }
    }

    /**
     * Handle INAPP status update notifications (Push notifications)
     * 
     * @param notificationDTO The status update data
     */
    private void handleInAppStatusUpdate(NotificationDTO notificationDTO) {
        log.debug("Processing INAPP status update for customerId: {}", notificationDTO.getCustomerId());

        // Check if this is a direct status update (notificationId = 1)
        if (isDirectStatusUpdate(notificationDTO.getNotificationId())) {
            log.info(ApplicationConstants.LOG_NOTIFICATION_ID_IS_DIRECT,
                    notificationDTO.getNotificationId());

            sendStatusUpdateNotification(notificationDTO);

        } else {
            log.debug("Notification ID is not direct status update. Would fetch template from database for ID: {}",
                    notificationDTO.getNotificationId());
            // TODO: Implement template fetching from database for other notification IDs
        }
    }

    /**
     * Handle EMAIL status update notifications
     * 
     * @param notificationDTO The status update data
     */
    private void handleEmailStatusUpdate(NotificationDTO notificationDTO) {
        log.debug("Processing EMAIL status update for customerId: {}", notificationDTO.getCustomerId());
        // TODO: Implement email sending logic
        log.info("Email status update handling not yet implemented for customerId: {}",
                notificationDTO.getCustomerId());
    }

    /**
     * Handle SMS status update notifications
     * 
     * @param notificationDTO The status update data
     */
    private void handleSmsStatusUpdate(NotificationDTO notificationDTO) {
        log.debug("Processing SMS status update for customerId: {}", notificationDTO.getCustomerId());
        // TODO: Implement SMS sending logic
        log.info("SMS status update handling not yet implemented for customerId: {}",
                notificationDTO.getCustomerId());
    }

    /**
     * Send status update push notification via FCM
     * 
     * @param notificationDTO The status update data
     */
    private void sendStatusUpdateNotification(NotificationDTO notificationDTO) {
        log.debug("Preparing to send status update notification for customerId: {}",
                notificationDTO.getCustomerId());

        // Fetch FCM token from database based on customerId
        String fcmToken = fetchFcmTokenFromDatabase(notificationDTO.getCustomerId());

        if (fcmToken == null || fcmToken.isEmpty()) {
            log.error(ApplicationConstants.LOG_FCM_TOKEN_NOT_FOUND, notificationDTO.getCustomerId());
            return;
        }

        // Log sender information
        log.info(ApplicationConstants.LOG_STATUS_UPDATE_SENDER_INFO,
                notificationDTO.getSenderCustomerId(),
                notificationDTO.getSenderMobile());

        // Get file IDs count for logging
        String fileIds = notificationDTO.getFileIds();
        int fileCount = fileIds != null ? fileIds.split(",").length : 0;

        log.info(ApplicationConstants.LOG_SENDING_STATUS_UPDATE_NOTIFICATION,
                fcmToken,
                fileCount);

        // Build data map for FCM
        Map<String, String> fcmDataMap = buildStatusUpdateDataMap(notificationDTO);

        // Send data-only notification (no notification object) for status updates
        // This allows the frontend to process it in the background even when locked.
        // For status updates, we use isSilent = true to set 'background' type for APNs
        String messageId = fcmUtil.sendDataOnlyPushNotification(
                fcmToken,
                fcmDataMap,
                true
        );

        if (messageId != null) {
            log.info(ApplicationConstants.LOG_STATUS_UPDATE_SENT_SUCCESSFULLY, messageId);
        } else {
            log.error(ApplicationConstants.LOG_STATUS_UPDATE_FAILED,
                    notificationDTO.getCustomerId());
        }
    }

    /**
     * Build FCM data map from status update DTO
     * 
     * @param notificationDTO The status update data
     * @return Map with FCM data
     */
    private Map<String, String> buildStatusUpdateDataMap(NotificationDTO notificationDTO) {
        Map<String, String> fcmData = new HashMap<>();

        // Add sender information
        if (notificationDTO.getSenderMobile() != null) {
            String mobile = notificationDTO.getSenderMobile();
            fcmData.put("senderPhone", mobile);
            fcmData.put(ApplicationConstants.FCM_SENDER_MOBILE_KEY, mobile);
        }

        if (notificationDTO.getSenderCustomerId() != null) {
            fcmData.put(ApplicationConstants.FCM_SENDER_CUSTOMER_ID_KEY,
                    notificationDTO.getSenderCustomerId());
        }

        // Add file IDs if present
        if (notificationDTO.getFileIds() != null) {
            fcmData.put(ApplicationConstants.FCM_FILE_IDS_KEY, notificationDTO.getFileIds());
        }

        // Add notification metadata
        fcmData.put("type", ApplicationConstants.FCM_NOTIFICATION_TYPE_STATUS);
        fcmData.put("sound", ApplicationConstants.FCM_NOTIFICATION_SOUND_NONE);
        fcmData.put("badge", "1");

        log.debug("Built FCM data map for status update with {} entries", fcmData.size());
        return fcmData;
    }

    /**
     * Check if notification is a direct status update
     * 
     * @param notificationId The notification ID to check
     * @return true if it's a direct status update, false otherwise
     */
    private boolean isDirectStatusUpdate(Long notificationId) {
        boolean isDirect = ApplicationConstants.NOTIFICATION_ID_DIRECT_MESSAGE.equals(notificationId);
        log.debug("Checking if notification is direct status update: notificationId={}, isDirect={}",
                notificationId, isDirect);
        return isDirect;
    }

    /**
     * Validate status update notification data
     * 
     * @param notificationDTO The notification to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidStatusUpdate(NotificationDTO notificationDTO) {
        if (notificationDTO == null) {
            log.error("NotificationDTO is null");
            return false;
        }

        if (notificationDTO.getCustomerId() == null) {
            log.error("Customer ID is null in status update notification");
            return false;
        }

        if (notificationDTO.getNotificationId() == null) {
            log.error("Notification ID is null in status update notification");
            return false;
        }

        if (notificationDTO.getChannel() == null) {
            log.error("Notification channel is null in status update notification");
            return false;
        }

        if (notificationDTO.getMap() == null || notificationDTO.getMap().isEmpty()) {
            log.error("Status update map is null or empty");
            return false;
        }

        log.debug("Status update validation passed for customerId: {}",
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
