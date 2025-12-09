package com.odin.notification.controller;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.odin.notification.dto.NotificationDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NotificationListener {

    @KafkaListener(topics = "${spring.kafka.topics.otp}", groupId = "${spring.kafka.consumer.group-id}")
    public void listenOtp(NotificationDTO notification) {
        log.info("Received OTP notification: {}", notification);
        // TODO: Forward to SMS/email service
    }

    @KafkaListener(topics = "${spring.kafka.topics.alert}", groupId = "${spring.kafka.consumer.group-id}")
    public void listenAlert(NotificationDTO notification) {
        log.info("Received Alert notification: {}", notification);
        // TODO: Forward to appropriate channel
    }

    @KafkaListener(topics = "${spring.kafka.topics.reminder}", groupId = "${spring.kafka.consumer.group-id}")
    public void listenReminder(NotificationDTO notification) {
        log.info("Received Reminder notification: {}", notification);
        // TODO: Forward to appropriate channel
    }
}
