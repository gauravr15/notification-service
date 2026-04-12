package com.odin.notification.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.odin.notification.constants.ApplicationConstants;
import com.odin.notification.dto.NotificationDTO;
import com.odin.notification.dto.PrivacyVisibilityChangeEvent;
import com.odin.notification.entity.NotificationToken;
import com.odin.notification.enums.NotificationChannel;
import com.odin.notification.repo.NotificationTokenRepository;
import com.odin.notification.service.PrivacyVisibilityChangeService;
import com.odin.notification.util.FcmUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Privacy Visibility Change Service Implementation.
 * Sends silent FCM push to contacts when a user changes privacy settings.
 * 
 * Behavior similar to WhatsApp - instant cache invalidation/refresh across devices.
 */
@Slf4j
@Service
public class PrivacyVisibilityChangeServiceImpl implements PrivacyVisibilityChangeService {

    private final FcmUtil fcmUtil;
    private final NotificationTokenRepository notificationTokenRepository;

    public PrivacyVisibilityChangeServiceImpl(FcmUtil fcmUtil, NotificationTokenRepository notificationTokenRepository) {
        this.fcmUtil = fcmUtil;
        this.notificationTokenRepository = notificationTokenRepository;
    }

    @Override
    public void processPrivacyVisibilityChange(PrivacyVisibilityChangeEvent event) {
        log.info("[PRIVACY-SERVICE] 🔒 Processing privacy visibility change event");
        
        String userId = event.getUserId();
        String photoPrivacy = event.getPhotoPrivacy();
        String lastSeenPrivacy = event.getLastSeenPrivacy();
        List<String> eligibleContacts = event.getEligibleContactIds();
        
        if (userId == null) {
            log.warn("[PRIVACY-SERVICE] ⚠️ Missing userId in privacy event");
            return;
        }

        log.info("[PRIVACY-SERVICE] 📊 Event Details: userId={}, photoPrivacy={}, lastSeenPrivacy={}",
                userId, photoPrivacy, lastSeenPrivacy);

        if (eligibleContacts == null || eligibleContacts.isEmpty()) {
            log.info("[PRIVACY-SERVICE] ℹ️ No eligible contacts for privacy change notification");
            return;
        }

        log.info("[PRIVACY-SERVICE] 👥 Sending notifications to {} eligible contacts", eligibleContacts.size());

        int successCount = 0;
        int failureCount = 0;
        String action = determineAction(event);

        for (String contactId : eligibleContacts) {
            try {
                String fcmToken = fetchFcmToken(contactId);
                if (fcmToken == null || fcmToken.isEmpty()) {
                    log.warn("[PRIVACY-SERVICE] ⚠️ No FCM token for contact: {}. Skipping.", contactId);
                    failureCount++;
                    continue;
                }

                Map<String, String> fcmData = buildFcmDataMap(userId, action, photoPrivacy, lastSeenPrivacy);
                String messageId = fcmUtil.sendDataOnlyPushNotification(fcmToken, fcmData, true);
                
                log.info("[PRIVACY-SERVICE] ✅ Privacy change FCM sent. MessageId={}, contactId={}, action=",
                        messageId, contactId, action);
                successCount++;

            } catch (Exception e) {
                log.error("[PRIVACY-SERVICE] ❌ Failed to send FCM to contact: {}. Error: {}", contactId, e.getMessage());
                failureCount++;
            }
        }

        log.info("[PRIVACY-SERVICE] 📈 FCM Notification Summary: Success={}, Failed={}, Total={}",
                successCount, failureCount, eligibleContacts.size());

        if (successCount > 0) {
            log.info("[PRIVACY-SERVICE] ✨ Privacy notifications successfully sent to {} contacts", successCount);
        }
    }

    /**
     * Determine action based on privacy levels.
     * GRANTED = visibility increased, REVOKED = visibility decreased
     */
    private String determineAction(PrivacyVisibilityChangeEvent event) {
        String oldPhoto = event.getOldPhotoPrivacy();
        String newPhoto = event.getPhotoPrivacy();
        
        // If privacy became more open (from NOBODY or MY_CONTACTS to something more open)
        boolean isPhotoGranted = isMoreOpen(oldPhoto, newPhoto);
        
        String oldLastSeen = event.getOldLastSeenPrivacy();
        String newLastSeen = event.getLastSeenPrivacy();
        boolean isLastSeenGranted = isMoreOpen(oldLastSeen, newLastSeen);
        
        // If either attribute became more open, consider it GRANTED
        if (isPhotoGranted || isLastSeenGranted) {
            return "GRANTED";
        }
        // Otherwise, visibility was restricted
        return "REVOKED";
    }
    
    /**
     * Check if newLevel is more open than oldLevel
     */
    private boolean isMoreOpen(String oldLevel, String newLevel) {
        if (oldLevel == null || newLevel == null) return false;
        
        // NOBODY is most restrictive, MY_CONTACTS is middle, EVERYONE is most open
        if (oldLevel.equals("NOBODY")) {
            return !newLevel.equals("NOBODY"); // Moved to something more open
        }
        if (oldLevel.equals("MY_CONTACTS")) {
            return newLevel.equals("EVERYONE"); // Moved to most open
        }
        return false; // EVERYONE is already most open
    }

    /**
     * Build FCM data payload for privacy change notification.
     * This is a silent notification (data-only, no sound).
     */
    private Map<String, String> buildFcmDataMap(
            String userId,
            String action,
            String photoPrivacy,
            String lastSeenPrivacy) {

        Map<String, String> fcmData = new HashMap<>();

        // Type: PRIVACY_CHANGE
        fcmData.put("type", "PRIVACY_CHANGE");
        fcmData.put("sound", ApplicationConstants.FCM_NOTIFICATION_SOUND_NONE);
        fcmData.put("badge", "0");

        // User who changed privacy
        fcmData.put("targetUserId", userId);
        fcmData.put("senderCustomerId", userId);

        // Privacy change details
        fcmData.put("action", action); // GRANTED or REVOKED
        fcmData.put("photoPrivacy", photoPrivacy != null ? photoPrivacy : "UNKNOWN");
        fcmData.put("lastSeenPrivacy", lastSeenPrivacy != null ? lastSeenPrivacy : "UNKNOWN");

        // Determine attributes
        StringBuilder attributes = new StringBuilder();
        if (photoPrivacy != null) {
            attributes.append("PHOTO");
        }
        if (lastSeenPrivacy != null) {
            if (attributes.length() > 0) {
                attributes.append(",");
            }
            attributes.append("LAST_SEEN");
        }
        fcmData.put("attributes", attributes.toString());

        log.debug("[PRIVACY-SERVICE] 📦 Built FCM payload with {} entries", fcmData.size());
        return fcmData;
    }

    /**
     * Fetch FCM token for a contact.
     */
    private String fetchFcmToken(String contactId) {
        try {
            Long contactIdLong = Long.parseLong(contactId);
            Optional<NotificationToken> token = notificationTokenRepository.findFirstByCustomerId(contactIdLong);
            
            if (token.isPresent() && token.get().getFcmToken() != null && !token.get().getFcmToken().isEmpty()) {
                log.debug("[PRIVACY-SERVICE] 🔑 Found FCM token for contact: {}", contactId);
                return token.get().getFcmToken();
            }
            
            log.debug("[PRIVACY-SERVICE] ℹ️ No valid FCM token for contact: {}", contactId);
            return null;
        } catch (NumberFormatException e) {
            log.error("[PRIVACY-SERVICE] ❌ Invalid contactId format: {}. Error: {}", contactId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[PRIVACY-SERVICE] ❌ Error fetching FCM token for contact: {}. Error: {}", contactId, e.getMessage());
            return null;
        }
    }
}
