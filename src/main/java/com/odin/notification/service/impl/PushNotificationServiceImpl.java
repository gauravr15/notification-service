package com.odin.notification.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;

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
    private final Fast2SmsOtpService fast2SmsOtpService;
    private final KafkaTemplate<String, NotificationDTO> kafkaTemplate;
    private static final String CALL_INVITE_TYPE = "CALL_INVITE";
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Non-retryable FCM error codes — retrying these would never succeed
    private static final Set<MessagingErrorCode> NON_RETRYABLE_ERRORS = Set.of(
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.SENDER_ID_MISMATCH,
            MessagingErrorCode.THIRD_PARTY_AUTH_ERROR
    );

    @Value("${fcm.retry.enabled:true}")
    private boolean fcmRetryEnabled;

    @Value("${fcm.retry.max.attempts:3}")
    private int fcmRetryMaxAttempts;

    @Value("${fcm.retry.initial.backoff.ms:1000}")
    private long fcmRetryInitialBackoffMs;

    @Value("${fcm.retry.backoff.multiplier:2.0}")
    private double fcmRetryBackoffMultiplier;

    @Value("${fcm.retry.max.backoff.ms:30000}")
    private long fcmRetryMaxBackoffMs;

    @Value("${fcm.failure.kafka.topic:fcm-failure-undelivered-messages}")
    private String fcmFailureKafkaTopic;

    @Value("${fcm.failure.kafka.publish.enabled:true}")
    private boolean fcmFailureKafkaPublishEnabled;

    public PushNotificationServiceImpl(FcmUtil fcmUtil, NotificationTokenRepository notificationTokenRepository,
                                       Fast2SmsOtpService fast2SmsOtpService,
                                       KafkaTemplate<String, NotificationDTO> kafkaTemplate) {
        this.fcmUtil = fcmUtil;
        this.notificationTokenRepository = notificationTokenRepository;
        this.fast2SmsOtpService = fast2SmsOtpService;
        this.kafkaTemplate = kafkaTemplate;
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

            // Determine whether this is a CALL_INVITE — call notifications don't carry a body text.
            String signalForCheck = notificationDTO.getMap() != null && notificationDTO.getMap().get("signal") != null
                    ? String.valueOf(notificationDTO.getMap().get("signal"))
                    : null;
            boolean isCallInviteNotif = CALL_INVITE_TYPE.equalsIgnoreCase(signalForCheck);
            // If it's not encrypted AND not a CALL_INVITE, we require a message body.
            // CALL_INVITE pushes carry no body text — they are routed to APNs VoIP or FCM data-only.
            if (!notificationDTO.isEncrypted() && !isCallInviteNotif && (message == null || message.isEmpty())) {
                log.warn("No message found in notification map for non-encrypted message, customerId: {}",
                        notificationDTO.getCustomerId());
                return;
            }
            if (isCallInviteNotif) {
                log.info("[CALL_INVITE] Bypassing message body requirement for CALL_INVITE notification, customerId={}",
                        notificationDTO.getCustomerId());
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

        String mobile = notificationDTO.getMobile();
        String otp = notificationDTO.getMap() != null
                ? String.valueOf(notificationDTO.getMap().get("otp"))
                : null;

        if (mobile == null || mobile.isBlank() || otp == null || "null".equals(otp)) {
            log.warn(ApplicationConstants.LOG_SMS_OTP_MISSING_DATA, mobile, otp != null);
            return;
        }

        boolean sent = fast2SmsOtpService.sendOtp(mobile, otp);
        if (sent) {
            log.info(ApplicationConstants.LOG_SMS_OTP_SENT_SUCCESS, mobile);
        } else {
            log.warn(ApplicationConstants.LOG_SMS_OTP_SENT_FAILURE, mobile);
        }
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

        // ── Phase 3: iOS VoIP push path for CALL_INVITE ──────────────────────
        // Decision tree for CALL_INVITE:
        //   1. customerId null              → skip VoIP block entirely (NPE guard)
        //   2. signal != CALL_INVITE        → skip VoIP block (all non-call notifications)
        //   3. signal == CALL_INVITE AND no voipToken row (Android, new iOS) → fall through to FCM ✅
        //   4. signal == CALL_INVITE AND voipToken present (registered iOS)  → APNs VoIP push, return ✅
        //
        // Android devices NEVER have a voipToken row because _registerVoipToken() is
        // only called from the iOS PKPushRegistryDelegate (PushKit is iOS-only).
        // Therefore Android always lands on case 3 and takes the original FCM path unchanged.
        // Check both "signal" (WebSocket path) and "type" (Kafka/FCM payload path) for CALL_INVITE.
        // Kafka consumer payloads use "type=CALL_INVITE" — "signal" is absent in that case.
        String signalField = null;
        if (notificationDTO.getMap() != null) {
            Object sig = notificationDTO.getMap().get("signal");
            Object typ = notificationDTO.getMap().get("type");
            if (sig != null) {
                signalField = String.valueOf(sig);
            } else if (typ != null) {
                signalField = String.valueOf(typ);
            }
        }
        log.debug("[VoIP-APNs] signalField resolved as '{}' for customerId={}", signalField, notificationDTO.getCustomerId());

        if (CALL_INVITE_TYPE.equalsIgnoreCase(signalField) && notificationDTO.getCustomerId() != null) {
            // Safe DB lookup — customerId non-null is guaranteed by the guard above
            Optional<NotificationToken> tokenRecord =
                    notificationTokenRepository.findFirstByCustomerId(notificationDTO.getCustomerId());

            String voipToken = tokenRecord.isPresent() ? tokenRecord.get().getVoipToken() : null;
            String deviceType = (tokenRecord.isPresent() && tokenRecord.get().getDeviceType() != null)
                    ? tokenRecord.get().getDeviceType()
                    : null;

            log.info("[VoIP-APNs] CALL_INVITE routing — customerId={} deviceType={} hasVoipToken={}",
                    notificationDTO.getCustomerId(), deviceType, voipToken != null && !voipToken.isBlank());

            // Double guard: if device_type is explicitly ANDROID, never route to APNs
            // even if a stale voipToken row exists (defensive — save() already clears it).
            if ("ANDROID".equalsIgnoreCase(deviceType)) {
                log.info("[VoIP-APNs] CALL_INVITE — device_type=ANDROID, skipping APNs and using FCM for customerId={}",
                        notificationDTO.getCustomerId());
                voipToken = null;
            }

            if (voipToken != null && !voipToken.isBlank()) {
                // ── iOS device with PushKit token registered → APNs VoIP push ──
                log.info("[VoIP-APNs] CALL_INVITE — iOS device with voipToken found for customerId={}, routing to APNs (bypassing FCM)",
                        notificationDTO.getCustomerId());
                Map<String, String> fcmDataMap = buildFcmDataMap(notificationDTO);
                if (fcmDataMap != null) {
                    int apnsStatus = fcmUtil.sendVoipApnsPush(voipToken, fcmDataMap);
                    log.info("[VoIP-APNs] APNs result: status={} customerId={}",
                            apnsStatus, notificationDTO.getCustomerId());
                    if (apnsStatus == 200) {
                        return; // APNs VoIP push delivered successfully — skip FCM
                    }
                    // APNs push failed — fall through to FCM so the call is not silently dropped
                    log.warn("[VoIP-APNs] ⚠️ APNs VoIP push failed (status={}) for customerId={} — falling back to FCM",
                            apnsStatus, notificationDTO.getCustomerId());
                } else {
                    log.warn("[VoIP-APNs] buildFcmDataMap returned null for customerId={}, falling back to FCM",
                            notificationDTO.getCustomerId());
                }
                // Fall through to standard FCM path below
            } else {
                // ── Android device OR new iOS device without voipToken yet ──
                // Fall through to the standard FCM path below (unchanged behaviour).
                log.debug("[VoIP-APNs] CALL_INVITE — no voipToken for customerId={} (Android or pre-registration iOS), using FCM",
                        notificationDTO.getCustomerId());
            }
        }
        // ─────────────────────────────────────────────────────────────────────

        // Fetch FCM token from database based on customerId
        String fcmToken = fetchFcmTokenFromDatabase(notificationDTO.getCustomerId());

        if (fcmToken == null || fcmToken.isEmpty()) {
            log.error(ApplicationConstants.LOG_FCM_TOKEN_NOT_FOUND, notificationDTO.getCustomerId());
            publishToFcmFailureTopic(notificationDTO, "FCM_TOKEN_NOT_FOUND");
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

        // Attempt FCM send with retry
        if (fcmRetryEnabled) {
            sendWithRetry(notificationDTO, fcmToken, fcmDataMap);
        } else {
            sendOnce(notificationDTO, fcmToken, fcmDataMap);
        }
    }

    /**
     * Send FCM with configurable retry and exponential backoff.
     * On terminal failure (retries exhausted or non-retryable error), publishes to Kafka fallback topic.
     */
    private void sendWithRetry(NotificationDTO notificationDTO, String fcmToken, Map<String, String> fcmDataMap) {
        long backoffMs = fcmRetryInitialBackoffMs;

        for (int attempt = 1; attempt <= fcmRetryMaxAttempts; attempt++) {
            try {
                String messageId = fcmUtil.sendDataOnlyPushNotification(fcmToken, fcmDataMap, false);
                log.info("[FCM-RETRY] Push sent successfully for customerId={}, attempt={}/{}, messageId={}",
                        notificationDTO.getCustomerId(), attempt, fcmRetryMaxAttempts, messageId);
                return; // Success — exit

            } catch (FirebaseMessagingException e) {
                MessagingErrorCode errorCode = e.getMessagingErrorCode();
                log.warn("[FCM-RETRY] Attempt {}/{} failed for customerId={}, errorCode={}, message={}",
                        attempt, fcmRetryMaxAttempts, notificationDTO.getCustomerId(), errorCode, e.getMessage());

                // Non-retryable errors — don't waste time retrying
                if (errorCode != null && NON_RETRYABLE_ERRORS.contains(errorCode)) {
                    log.error("[FCM-RETRY] Non-retryable error {} for customerId={}, publishing to fallback",
                            errorCode, notificationDTO.getCustomerId());
                    publishToFcmFailureTopic(notificationDTO,
                            "NON_RETRYABLE:" + errorCode.name());
                    return;
                }

                // Last attempt — publish to fallback
                if (attempt == fcmRetryMaxAttempts) {
                    log.error("[FCM-RETRY] All {} attempts exhausted for customerId={}, publishing to fallback",
                            fcmRetryMaxAttempts, notificationDTO.getCustomerId());
                    publishToFcmFailureTopic(notificationDTO,
                            "RETRIES_EXHAUSTED:" + (errorCode != null ? errorCode.name() : "UNKNOWN"));
                    return;
                }

                // Backoff before next attempt
                sleep(backoffMs);
                backoffMs = Math.min((long) (backoffMs * fcmRetryBackoffMultiplier), fcmRetryMaxBackoffMs);

            } catch (Exception e) {
                log.error("[FCM-RETRY] Unexpected error on attempt {}/{} for customerId={}: {}",
                        attempt, fcmRetryMaxAttempts, notificationDTO.getCustomerId(), e.getMessage(), e);

                if (attempt == fcmRetryMaxAttempts) {
                    publishToFcmFailureTopic(notificationDTO, "UNEXPECTED_ERROR");
                    return;
                }

                sleep(backoffMs);
                backoffMs = Math.min((long) (backoffMs * fcmRetryBackoffMultiplier), fcmRetryMaxBackoffMs);
            }
        }
    }

    /**
     * Single FCM attempt (retry disabled). On failure, publishes to Kafka fallback topic.
     */
    private void sendOnce(NotificationDTO notificationDTO, String fcmToken, Map<String, String> fcmDataMap) {
        try {
            String messageId = fcmUtil.sendDataOnlyPushNotification(fcmToken, fcmDataMap, false);
            log.info("Push notification sent successfully for customerId: {}, messageId: {}",
                    notificationDTO.getCustomerId(), messageId);
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send push notification for customerId: {}, error: {}",
                    notificationDTO.getCustomerId(), e.getMessage(), e);
            publishToFcmFailureTopic(notificationDTO,
                    "FCM_ERROR:" + (e.getMessagingErrorCode() != null ? e.getMessagingErrorCode().name() : "UNKNOWN"));
        } catch (Exception e) {
            log.error("Unexpected error sending push notification for customerId: {}: {}",
                    notificationDTO.getCustomerId(), e.getMessage(), e);
            publishToFcmFailureTopic(notificationDTO, "UNEXPECTED_ERROR");
        }
    }

    /**
     * Publish the original NotificationDTO to the FCM failure Kafka topic.
     * The websocket-service consumer will pick this up and store the message in Redis
     * as an undelivered message, ensuring the user gets it on next login.
     */
    private void publishToFcmFailureTopic(NotificationDTO notificationDTO, String failureReason) {
        if (!fcmFailureKafkaPublishEnabled) {
            log.info("[FCM-FALLBACK] Kafka publish disabled, skipping for customerId={}", notificationDTO.getCustomerId());
            return;
        }

        try {
            // Add failure metadata to the map so consumer can log/audit
            if (notificationDTO.getMap() != null) {
                notificationDTO.getMap().put("fcmFailureReason", failureReason);
            }

            String kafkaKey = "fcm-failure:" + notificationDTO.getCustomerId();
            kafkaTemplate.send(fcmFailureKafkaTopic, kafkaKey, notificationDTO);

            log.info("[FCM-FALLBACK] Published to topic={} for customerId={}, reason={}",
                    fcmFailureKafkaTopic, notificationDTO.getCustomerId(), failureReason);
        } catch (Exception e) {
            // Critical: Kafka publish failed too. Log prominently but message is still safe in Redis.
            log.error("[FCM-FALLBACK-CRITICAL] Failed to publish to Kafka for customerId={}, reason={}: {}. " +
                            "Message is still available in Redis undelivered store.",
                    notificationDTO.getCustomerId(), failureReason, e.getMessage(), e);
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[FCM-RETRY] Retry sleep interrupted");
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

        // Add all notification map entries, preserving structured payloads for CALL_INVITE
        if (map != null) {
            map.forEach((key, value) -> {
                if (value == null) {
                    return;
                }
                if (value instanceof java.util.List) {
                    String joined = ((java.util.List<?>) value).stream()
                            .map(String::valueOf)
                            .collect(java.util.stream.Collectors.joining(","));
                    fcmData.put(key, joined);
                } else if (value instanceof Map || value instanceof JsonNode) {
                    fcmData.put(key, toJsonString(key, value));
                } else {
                    fcmData.put(key, String.valueOf(value));
                }
            });
        }

        // Use only resolved values for FCM payload
        fcmData.putIfAbsent("conversationId", conversationId);
        if (groupId != null) {
            fcmData.putIfAbsent("groupId", groupId);
        }
        fcmData.putIfAbsent("isGroup", String.valueOf(isGroup));
        if (groupName != null) {
            fcmData.putIfAbsent("groupName", groupName);
        }

        log.info(
            "[FCM-BUILD] conversationId={} groupId={} isGroup={} groupName={}",
            conversationId, groupId, isGroup, groupName
        );

        // Add mandatory fields for data-only message rendering
        String resolvedType = ApplicationConstants.FCM_NOTIFICATION_TYPE_MESSAGE;
        if (map != null && map.get("type") != null) {
            String candidate = String.valueOf(map.get("type"));
            if (StringUtils.hasText(candidate)) {
                resolvedType = candidate;
            }
        }
        String signal = map != null && map.get("signal") != null
                ? String.valueOf(map.get("signal"))
                : null;
        if (CALL_INVITE_TYPE.equalsIgnoreCase(signal)) {
            resolvedType = CALL_INVITE_TYPE;
        }
        boolean isCallInvite = CALL_INVITE_TYPE.equalsIgnoreCase(resolvedType);
        fcmData.put("type", resolvedType);
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
        if (isCallInvite) {
            log.info("CALL_INVITE FCM STRUCTURE: {}", fcmData);
            try {
                log.info("========== BACKEND FINAL FCM DATA ==========");
                log.info("{}", objectMapper.writeValueAsString(fcmData));
                log.info("============================================");
            } catch (Exception e) {
                log.error("Failed to serialize FCM data map for logging", e);
            }
        }
        log.info("FINAL FCM DATA PAYLOAD: {}", fcmData);
        log.info("========== FINAL FCM DATA MAP ==========");
        log.info("{}", fcmData);
        log.info("========================================");
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

    private String toJsonString(String key, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("[FCM] Failed to serialize {} field for FCM payload: {}", key, e.getMessage());
            return String.valueOf(value);
        }
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
