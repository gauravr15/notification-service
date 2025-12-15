package com.odin.notification.controller;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.odin.notification.constants.ApplicationConstants;
import com.odin.notification.dto.NotificationDTO;
import com.odin.notification.service.PushNotificationService;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumer for handling undelivered notifications
 * Listens to the undelivered.notification.message Kafka topic
 * and triggers push notification sending logic
 */
@Slf4j
@Component
public class UndeliveredNotificationConsumer {

    private final PushNotificationService pushNotificationService;

    public UndeliveredNotificationConsumer(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    /**
     * Listen to undelivered notification messages from Kafka topic
     * and process them for push notification delivery
     * 
     * @param notificationDTO The notification data received from Kafka
     */
    @KafkaListener(
            topics = ApplicationConstants.KAFKA_UNDELIVERED_NOTIFICATION_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listenUndeliveredNotification(NotificationDTO notificationDTO) {
        log.info("=== Undelivered Notification Consumer Started ===");
        log.info("Received undelivered notification message from Kafka topic: {}",
                ApplicationConstants.KAFKA_UNDELIVERED_NOTIFICATION_TOPIC);
        log.debug("Notification details - CustomerId: {}, NotificationId: {}, Channel: {}",
                notificationDTO.getCustomerId(),
                notificationDTO.getNotificationId(),
                notificationDTO.getChannel());

        try {
            // Process the notification through push notification service
            pushNotificationService.processPushNotification(notificationDTO);

            log.info("=== Undelivered Notification Consumer Completed Successfully ===");

        } catch (Exception e) {
            log.error("Error processing undelivered notification for customerId: {}. Error: {}",
                    notificationDTO.getCustomerId(),
                    e.getMessage(),
                    e);
            log.error("=== Undelivered Notification Consumer Failed ===");
            // Note: In production, consider implementing retry logic or dead letter queue handling
        }
    }
}
