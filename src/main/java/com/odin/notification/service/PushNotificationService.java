package com.odin.notification.service;

import com.odin.notification.dto.NotificationDTO;

/**
 * Push Notification Service Interface
 * Defines contracts for processing and sending push notifications
 */
public interface PushNotificationService {

    /**
     * Process undelivered notification and send push notification
     * 
     * @param notificationDTO The notification data transfer object
     */
    void processPushNotification(NotificationDTO notificationDTO);
}
