package com.odin.notification.service;

import com.odin.notification.dto.PrivacyVisibilityChangeEvent;

/**
 * Privacy Visibility Change Service Interface
 * Defines contracts for processing privacy visibility changes
 */
public interface PrivacyVisibilityChangeService {

    /**
     * Process privacy visibility change event and send FCM notifications
     * 
     * @param event The privacy visibility change event with user, privacy levels, and contacts
     */
    void processPrivacyVisibilityChange(PrivacyVisibilityChangeEvent event);
}
