package com.odin.notification.service;

import com.odin.notification.dto.NotificationDTO;

public interface ProfilePhotoUpdateService {
    void processProfilePhotoUpdateNotification(NotificationDTO notificationDTO);
}
