package com.odin.notification.controller;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.odin.notification.constants.ApplicationConstants;
import com.odin.notification.dto.NotificationDTO;
import com.odin.notification.service.PrivacyVisibilityChangeService;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Consumer for handling privacy visibility change notifications.
 * Listens to the privacy-visibility-updates Kafka topic
 * and triggers silent FCM push to notify contacts of privacy changes.
 */
@Slf4j
@Component
public class PrivacyVisibilityChangeConsumer {

    private final PrivacyVisibilityChangeService privacyVisibilityChangeService;

    public PrivacyVisibilityChangeConsumer(PrivacyVisibilityChangeService privacyVisibilityChangeService) {
        this.privacyVisibilityChangeService = privacyVisibilityChangeService;
    }

    @KafkaListener(
            topics = "privacy-visibility-updates",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listenPrivacyVisibilityChange(Map<String, Object> event) {
        log.info("=== Privacy Visibility Change Consumer Started ===");
        
        String userId = event.get("userId") != null ? event.get("userId").toString() : "UNKNOWN";
        String action = event.get("action") != null ? event.get("action").toString() : "UNKNOWN";
        int contactCount = event.get("contactCount") != null ? (Integer) event.get("contactCount") : 0;
        
        log.info("[PRIVACY-CONSUMER] 📥 Received privacy change event: userId={}, action={}, contacts={}, timestamp={}",
                userId, action, contactCount, event.get("timestamp"));

        try {
            privacyVisibilityChangeService.processPrivacyVisibilityChange(event);
            log.info("[PRIVACY-CONSUMER] ✅ Privacy visibility change processing completed for userId={}", userId);
        } catch (Exception e) {
            log.error("[PRIVACY-CONSUMER] ❌ Error processing privacy visibility change for userId={}. Error: {}",
                    userId, e.getMessage(), e);
        }
    }
}
