package com.odin.notification.controller;

import java.time.Duration;
import java.util.Map;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.odin.notification.dto.NotificationDTO;
import com.odin.notification.service.PushNotificationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NotificationEventsConsumer {

    private static final String CALL_INVITE_TYPE = "CALL_INVITE";
    private static final String CALL_INVITE_KEY_PREFIX = "call_invite";
    private static final Duration CALL_INVITE_TTL = Duration.ofSeconds(60);

    private final PushNotificationService pushNotificationService;
    private final RedisTemplate<String, String> redisTemplate;

    public NotificationEventsConsumer(PushNotificationService pushNotificationService,
            RedisTemplate<String, String> redisTemplate) {
        this.pushNotificationService = pushNotificationService;
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(
            topics = "notification-events",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeNotificationEvent(NotificationDTO notificationDTO) {
        if (notificationDTO == null) {
            log.warn("Received null NotificationDTO, skipping processing");
            return;
        }

        Map<String, Object> payload = notificationDTO.getMap();
        if (payload == null) {
            log.warn("Notification map missing for customerId={}, skipping", notificationDTO.getCustomerId());
            return;
        }

        String type = safeToString(payload.get("type"));
        String messageId = safeToString(payload.get("messageId"));
        String receiverCustomerId = safeToString(payload.get("receiverCustomerId"));

        log.info("Received notification event type={} messageId={} receiver={}", type, messageId, receiverCustomerId);

        if (type == null || messageId == null || receiverCustomerId == null) {
            log.warn("Required fields missing, cannot process notification event: type={}, messageId={}, receiver={}",
                    type, messageId, receiverCustomerId);
            return;
        }

        if (CALL_INVITE_TYPE.equalsIgnoreCase(type)) {
            String redisKey = buildCallInviteKey(receiverCustomerId, messageId);
            Boolean claimed = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", CALL_INVITE_TTL);
            if (Boolean.FALSE.equals(claimed)) {
                log.info("Duplicate CALL_INVITE ignored for receiver={} messageId={}", receiverCustomerId, messageId);
                return;
            }
        }

        try {
            pushNotificationService.processPushNotification(notificationDTO);
            log.info("Notification processed successfully for receiver={} messageId={}", receiverCustomerId, messageId);
        } catch (Exception e) {
            log.error("Error processing notification event messageId={}", messageId, e);
        }
    }

    private String buildCallInviteKey(String receiverCustomerId, String messageId) {
        return String.format("%s:%s:%s", CALL_INVITE_KEY_PREFIX, receiverCustomerId, messageId);
    }

    private String safeToString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
