package com.odin.notification.service;

import com.odin.notification.dto.NotificationDTO;

/**
 * Status Update Notification Service Interface
 * Defines the contract for processing status update notifications
 */
public interface StatusUpdateService {

    /**
     * Process status update notification
     * 
     * @param notificationDTO The status update notification data
     */
    void processStatusUpdateNotification(NotificationDTO notificationDTO);
}
