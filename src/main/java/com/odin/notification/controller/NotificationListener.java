package com.odin.notification.controller;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.odin.notification.constants.ApplicationConstants;
import com.odin.notification.dto.NotificationDTO;
import com.odin.notification.enums.NotificationChannel;
import com.odin.notification.service.impl.Fast2SmsOtpService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NotificationListener {

    private final Fast2SmsOtpService fast2SmsOtpService;

    public NotificationListener(Fast2SmsOtpService fast2SmsOtpService) {
        this.fast2SmsOtpService = fast2SmsOtpService;
    }

    @KafkaListener(topics = ApplicationConstants.KAFKA_OTP_NOTIFICATION_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void listenOtp(NotificationDTO notification) {
        log.info("Received OTP notification: {}", notification);

        if (notification.getChannel() == NotificationChannel.SMS) {
            String mobile = notification.getMobile();
            String otp = notification.getMap() != null
                    ? String.valueOf(notification.getMap().get("otp"))
                    : null;

            log.info(ApplicationConstants.LOG_SMS_OTP_RECEIVED, mobile);

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
        } else {
            log.info("OTP notification channel is {} — skipping SMS send", notification.getChannel());
        }
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
