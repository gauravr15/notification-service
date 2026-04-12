package com.odin.notification.controller;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.odin.notification.constants.ApplicationConstants;
import com.odin.notification.dto.NotificationDTO;
import com.odin.notification.dto.PrivacyVisibilityChangeEvent;
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
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "privacyVisibilityChangeListenerFactory"
    )
    public void listenPrivacyVisibilityChange(PrivacyVisibilityChangeEvent event) {
        log.info("=== Privacy Visibility Change Consumer Started ===");
        
        String userId = event.getUserId() != null ? event.getUserId() : "UNKNOWN";
        int contactCount = event.getEligibleContactIds() != null ? event.getEligibleContactIds().size() : 0;
        
        log.info("[PRIVACY-CONSUMER] 📥 Received privacy change event: userId={}, photo={}, lastSeen={}, contacts={}, timestamp={}",
                userId, event.getPhotoPrivacy(), event.getLastSeenPrivacy(), contactCount, event.getTimestamp());

        try {
            privacyVisibilityChangeService.processPrivacyVisibilityChange(event);
            log.info("[PRIVACY-CONSUMER] ✅ Privacy visibility change processing completed for userId={}", userId);
        } catch (Exception e) {
            log.error("[PRIVACY-CONSUMER] ❌ Error processing privacy visibility change for userId={}. Error: {}",
                    userId, e.getMessage(), e);
        }
    }
}
