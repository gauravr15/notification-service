package com.odin.notification.controller;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.odin.notification.constants.ApplicationConstants;
import com.odin.notification.dto.NotificationDTO;
import com.odin.notification.service.ProfilePhotoUpdateService;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumer for handling profile photo update notifications.
 * Listens to the profile-image-updates Kafka topic
 * and triggers silent FCM push to notify contacts of the photo change.
 */
@Slf4j
@Component
public class ProfilePhotoUpdateConsumer {

    private final ProfilePhotoUpdateService profilePhotoUpdateService;

    public ProfilePhotoUpdateConsumer(ProfilePhotoUpdateService profilePhotoUpdateService) {
        this.profilePhotoUpdateService = profilePhotoUpdateService;
    }

    @KafkaListener(
            topics = ApplicationConstants.KAFKA_PROFILE_PHOTO_UPDATE_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listenProfilePhotoUpdate(NotificationDTO notificationDTO) {
        log.info("=== Profile Photo Update Consumer Started ===");
        log.info("Received profile photo update notification from Kafka topic: {}",
                ApplicationConstants.KAFKA_PROFILE_PHOTO_UPDATE_TOPIC);
        log.debug("Profile photo update details - CustomerId: {}, NotificationId: {}, Channel: {}",
                notificationDTO.getCustomerId(),
                notificationDTO.getNotificationId(),
                notificationDTO.getChannel());

        try {
            profilePhotoUpdateService.processProfilePhotoUpdateNotification(notificationDTO);
            log.info("=== Profile Photo Update Consumer Completed Successfully ===");
        } catch (Exception e) {
            log.error("Error processing profile photo update notification for customerId: {}. Error: {}",
                    notificationDTO.getCustomerId(),
                    e.getMessage(),
                    e);
        }
    }
}
