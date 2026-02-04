package com.odin.notification.controller;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.odin.notification.constants.ApplicationConstants;
import com.odin.notification.dto.NotificationDTO;
import com.odin.notification.service.StatusUpdateService;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumer for handling status update notifications
 * Listens to the status.update.notification.message Kafka topic
 * and triggers status update push notification sending logic
 */
@Slf4j
@Component
public class StatusUpdateConsumer {

    private final StatusUpdateService statusUpdateService;

    public StatusUpdateConsumer(StatusUpdateService statusUpdateService) {
        this.statusUpdateService = statusUpdateService;
    }

    /**
     * Listen to status update notification messages from Kafka topic
     * and process them for push notification delivery
     * 
     * @param notificationDTO The status update notification data received from Kafka
     */
    @KafkaListener(
            topics = ApplicationConstants.KAFKA_STATUS_UPDATE_NOTIFICATION_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listenStatusUpdate(NotificationDTO notificationDTO) {
        log.info("=== Status Update Consumer Started ===");
        log.info("Received status update notification message from Kafka topic: {}",
                ApplicationConstants.KAFKA_STATUS_UPDATE_NOTIFICATION_TOPIC);
        log.debug("Status update details - CustomerId: {}, NotificationId: {}, Channel: {}",
                notificationDTO.getCustomerId(),
                notificationDTO.getNotificationId(),
                notificationDTO.getChannel());

        try {
            // Process the status update through status update service
            statusUpdateService.processStatusUpdateNotification(notificationDTO);

            log.info("=== Status Update Consumer Completed Successfully ===");

        } catch (Exception e) {
            log.error("Error processing status update notification for customerId: {}. Error: {}",
                    notificationDTO.getCustomerId(),
                    e.getMessage(),
                    e);
            log.error("=== Status Update Consumer Failed ===");
            // Note: In production, consider implementing retry logic or dead letter queue handling
        }
    }
}
