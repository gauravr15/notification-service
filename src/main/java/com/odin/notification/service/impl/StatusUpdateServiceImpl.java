package com.odin.notification.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;

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
 * Handles the business logic for processing and sending status update push notifications.
 *
 * Now includes:
 *   - Configurable FCM retry with exponential backoff (mirrors PushNotificationServiceImpl)
 *   - Kafka fallback on terminal FCM failure → status-fcm-failure-undelivered topic
 *   - Token-missing fallback → publish to Kafka so status metadata is stored in Redis
 */
@Slf4j
@Service
public class StatusUpdateServiceImpl implements StatusUpdateService {

    private final FcmUtil fcmUtil;
    private final NotificationTokenRepository notificationTokenRepository;
    private final KafkaTemplate<String, NotificationDTO> kafkaTemplate;

    // Non-retryable FCM error codes — retrying these would never succeed
    private static final Set<MessagingErrorCode> NON_RETRYABLE_ERRORS = Set.of(
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.SENDER_ID_MISMATCH,
            MessagingErrorCode.THIRD_PARTY_AUTH_ERROR
    );

    // ===== Status FCM Retry Configuration (new property added) =====
    @Value("${status.fcm.retry.enabled:true}")
    private boolean statusFcmRetryEnabled;

    @Value("${status.fcm.retry.max.attempts:3}")
    private int statusFcmRetryMaxAttempts;

    @Value("${status.fcm.retry.initial.backoff.ms:1000}")
    private long statusFcmRetryInitialBackoffMs;

    @Value("${status.fcm.retry.backoff.multiplier:2.0}")
    private double statusFcmRetryBackoffMultiplier;

    @Value("${status.fcm.retry.max.backoff.ms:30000}")
    private long statusFcmRetryMaxBackoffMs;

    // ===== Status FCM Failure Kafka Fallback (new property added) =====
    @Value("${status.fcm.failure.kafka.topic:status-fcm-failure-undelivered}")
    private String statusFcmFailureKafkaTopic;

    @Value("${status.fcm.failure.kafka.publish.enabled:true}")
    private boolean statusFcmFailureKafkaPublishEnabled;

    public StatusUpdateServiceImpl(FcmUtil fcmUtil,
                                   NotificationTokenRepository notificationTokenRepository,
                                   KafkaTemplate<String, NotificationDTO> kafkaTemplate) {
        this.fcmUtil = fcmUtil;
        this.notificationTokenRepository = notificationTokenRepository;
        this.kafkaTemplate = kafkaTemplate;
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
     * Send status update push notification via FCM with retry and Kafka fallback.
     *
     * Flow:
     * 1. Fetch FCM token from DB
     * 2. If token missing → publish to Kafka fallback (token-missing path)
     * 3. Attempt FCM send with configurable retry + exponential backoff
     * 4. On terminal failure → publish to Kafka fallback topic
     *
     * @param notificationDTO The status update data
     */
    private void sendStatusUpdateNotification(NotificationDTO notificationDTO) {
        log.info("[STATUS-FCM] Preparing to send status notification for customerId={}",
                notificationDTO.getCustomerId());

        // Fetch FCM token from database based on customerId
        String fcmToken = fetchFcmTokenFromDatabase(notificationDTO.getCustomerId());

        // ── Token-missing fallback (Phase 6) ──
        if (fcmToken == null || fcmToken.isEmpty()) {
            log.warn("[STATUS-FCM] FCM token not found for customerId={}. " +
                    "Publishing to Kafka fallback so status metadata is stored in Redis.",
                    notificationDTO.getCustomerId());
            publishStatusToFcmFailureTopic(notificationDTO, "TOKEN_MISSING");
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
                fcmToken, fileCount);

        // Build data map for FCM
        Map<String, String> fcmDataMap = buildStatusUpdateDataMap(notificationDTO);

        // ── Attempt FCM send with retry or single-shot ──
        if (statusFcmRetryEnabled) {
            sendStatusWithRetry(notificationDTO, fcmToken, fcmDataMap);
        } else {
            sendStatusOnce(notificationDTO, fcmToken, fcmDataMap);
        }
    }

    /**
     * Send status FCM with configurable retry and exponential backoff.
     * On terminal failure (retries exhausted or non-retryable error), publishes to Kafka fallback topic.
     */
    private void sendStatusWithRetry(NotificationDTO notificationDTO, String fcmToken,
                                     Map<String, String> fcmDataMap) {
        long backoffMs = statusFcmRetryInitialBackoffMs;

        for (int attempt = 1; attempt <= statusFcmRetryMaxAttempts; attempt++) {
            try {
                String messageId = fcmUtil.sendDataOnlyPushNotification(fcmToken, fcmDataMap, true);
                log.info("[STATUS-FCM-RETRY] Push sent successfully for customerId={}, attempt={}/{}, messageId={}",
                        notificationDTO.getCustomerId(), attempt, statusFcmRetryMaxAttempts, messageId);
                return; // Success — exit

            } catch (FirebaseMessagingException e) {
                MessagingErrorCode errorCode = e.getMessagingErrorCode();
                log.warn("[STATUS-FCM-RETRY] Attempt {}/{} failed for customerId={}, errorCode={}, message={}",
                        attempt, statusFcmRetryMaxAttempts, notificationDTO.getCustomerId(),
                        errorCode, e.getMessage());

                // Non-retryable errors — don't waste time retrying
                if (errorCode != null && NON_RETRYABLE_ERRORS.contains(errorCode)) {
                    log.error("[STATUS-FCM-RETRY] Non-retryable error {} for customerId={}, publishing to fallback",
                            errorCode, notificationDTO.getCustomerId());
                    publishStatusToFcmFailureTopic(notificationDTO,
                            "NON_RETRYABLE:" + errorCode.name());
                    return;
                }

                // Last attempt — publish to fallback
                if (attempt == statusFcmRetryMaxAttempts) {
                    log.error("[STATUS-FCM-RETRY] All {} attempts exhausted for customerId={}, publishing to fallback",
                            statusFcmRetryMaxAttempts, notificationDTO.getCustomerId());
                    publishStatusToFcmFailureTopic(notificationDTO,
                            "RETRIES_EXHAUSTED:" + (errorCode != null ? errorCode.name() : "UNKNOWN"));
                    return;
                }

                // Backoff before next attempt
                sleep(backoffMs);
                backoffMs = Math.min((long) (backoffMs * statusFcmRetryBackoffMultiplier),
                        statusFcmRetryMaxBackoffMs);

            } catch (Exception e) {
                log.error("[STATUS-FCM-RETRY] Unexpected error on attempt {}/{} for customerId={}: {}",
                        attempt, statusFcmRetryMaxAttempts, notificationDTO.getCustomerId(),
                        e.getMessage(), e);

                if (attempt == statusFcmRetryMaxAttempts) {
                    publishStatusToFcmFailureTopic(notificationDTO, "UNEXPECTED_ERROR");
                    return;
                }

                sleep(backoffMs);
                backoffMs = Math.min((long) (backoffMs * statusFcmRetryBackoffMultiplier),
                        statusFcmRetryMaxBackoffMs);
            }
        }
    }

    /**
     * Single status FCM attempt (retry disabled). On failure, publishes to Kafka fallback topic.
     */
    private void sendStatusOnce(NotificationDTO notificationDTO, String fcmToken,
                                Map<String, String> fcmDataMap) {
        try {
            String messageId = fcmUtil.sendDataOnlyPushNotification(fcmToken, fcmDataMap, true);
            log.info("[STATUS-FCM] Status notification sent successfully for customerId={}, messageId={}",
                    notificationDTO.getCustomerId(), messageId);
        } catch (FirebaseMessagingException e) {
            log.error("[STATUS-FCM] Failed to send status notification for customerId={}, error={}",
                    notificationDTO.getCustomerId(), e.getMessage(), e);
            publishStatusToFcmFailureTopic(notificationDTO,
                    "FCM_ERROR:" + (e.getMessagingErrorCode() != null ? e.getMessagingErrorCode().name() : "UNKNOWN"));
        } catch (Exception e) {
            log.error("[STATUS-FCM] Unexpected error sending status notification for customerId={}: {}",
                    notificationDTO.getCustomerId(), e.getMessage(), e);
            publishStatusToFcmFailureTopic(notificationDTO, "UNEXPECTED_ERROR");
        }
    }

    /**
     * Publish the original NotificationDTO to the status FCM failure Kafka topic.
     * The web-socket-service FcmFailureStatusConsumer will pick this up and store
     * status metadata in Redis as STATUS:UNDELIVERED:{receiverId}, ensuring the
     * Flutter client gets it on next reconnect.
     */
    private void publishStatusToFcmFailureTopic(NotificationDTO notificationDTO, String failureReason) {
        if (!statusFcmFailureKafkaPublishEnabled) {
            log.info("[STATUS-FCM-FALLBACK] Kafka publish disabled, skipping for customerId={}",
                    notificationDTO.getCustomerId());
            return;
        }

        try {
            // Add failure metadata and timestamp so the consumer can build StatusNotificationMetadata
            if (notificationDTO.getMap() != null) {
                notificationDTO.getMap().put("fcmFailureReason", failureReason);
                // Ensure timestamp is present for Redis hash field generation
                notificationDTO.getMap().putIfAbsent("timestamp",
                        String.valueOf(System.currentTimeMillis()));
            }

            String kafkaKey = "status-fcm-failure:" + notificationDTO.getCustomerId();
            kafkaTemplate.send(statusFcmFailureKafkaTopic, kafkaKey, notificationDTO);

            log.info("[STATUS-FCM-FALLBACK] Published to topic={} for customerId={}, reason={}",
                    statusFcmFailureKafkaTopic, notificationDTO.getCustomerId(), failureReason);
        } catch (Exception e) {
            log.error("[STATUS-FCM-FALLBACK-CRITICAL] Failed to publish to Kafka for customerId={}, " +
                            "reason={}: {}. Status notification is LOST.",
                    notificationDTO.getCustomerId(), failureReason, e.getMessage(), e);
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[STATUS-FCM-RETRY] Retry sleep interrupted");
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

        // Detect if this is a STATUS_DELETE signal from the map
        boolean isDeleteSignal = notificationDTO.getMap() != null
                && "STATUS_DELETE".equals(notificationDTO.getMap().get("statusDeleteSignal"));

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

        // Set type based on whether this is a delete or an upload notification
        if (isDeleteSignal) {
            fcmData.put("type", ApplicationConstants.FCM_NOTIFICATION_TYPE_STATUS_DELETE);
            log.info("[STATUS-DELETE] Building FCM data for status deletion. customerId={}", notificationDTO.getCustomerId());
        } else {
            fcmData.put("type", ApplicationConstants.FCM_NOTIFICATION_TYPE_STATUS);
        }
        fcmData.put("sound", ApplicationConstants.FCM_NOTIFICATION_SOUND_NONE);
        fcmData.put("badge", "1");

        log.debug("Built FCM data map for status {} with {} entries", isDeleteSignal ? "delete" : "update", fcmData.size());
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
