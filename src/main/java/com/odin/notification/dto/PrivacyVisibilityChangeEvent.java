package com.odin.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Event payload received from Kafka for privacy visibility changes.
 * Published by profile-service when user updates privacy settings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyVisibilityChangeEvent {

    private String userId;
    
    // New privacy levels after change
    private String photoPrivacy;
    private String lastSeenPrivacy;
    
    // Old privacy levels before change (for context)
    private String oldPhotoPrivacy;
    private String oldLastSeenPrivacy;
    
    // Contacts eligible to receive FCM notification
    private List<String> eligibleContactIds;
    
    private long timestamp;
}
