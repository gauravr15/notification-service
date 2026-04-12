package com.odin.notification.service;

import java.util.Map;

/**
 * Privacy Visibility Change Service Interface
 * Defines contracts for processing privacy visibility changes
 */
public interface PrivacyVisibilityChangeService {

    /**
     * Process privacy visibility change event and send FCM notifications
     * 
     * @param event The event data with userId, action, contactCount, etc.
     */
    void processPrivacyVisibilityChange(Map<String, Object> event);
}
