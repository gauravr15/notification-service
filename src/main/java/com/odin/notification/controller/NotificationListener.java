package com.odin.notification.controller;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.odin.notification.constants.ApplicationConstants;
import com.odin.notification.dto.NotificationDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NotificationListener {

    @KafkaListener(topics = ApplicationConstants.KAFKA_OTP_NOTIFICATION_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void listenOtp(NotificationDTO notification) {
        log.info("Received OTP notification: {}", notification);
        // TODO: Forward to SMS/email service
    }

    @KafkaListener(topics = ApplicationConstants.KAFKA_ALERT_NOTIFICATION_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void listenAlert(NotificationDTO notification) {
        log.info("Received Alert notification: {}", notification);
        // TODO: Forward to appropriate channel
    }

    @KafkaListener(topics = ApplicationConstants.KAFKA_REMINDER_NOTIFICATION_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void listenReminder(NotificationDTO notification) {
        log.info("Received Reminder notification: {}", notification);
        // TODO: Forward to appropriate channel
    }
}
