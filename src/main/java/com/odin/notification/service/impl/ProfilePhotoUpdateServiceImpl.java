package com.odin.notification.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.odin.notification.constants.ApplicationConstants;
import com.odin.notification.dto.NotificationDTO;
import com.odin.notification.entity.NotificationToken;
import com.odin.notification.enums.NotificationChannel;
import com.odin.notification.repo.NotificationTokenRepository;
import com.odin.notification.service.ProfilePhotoUpdateService;
import com.odin.notification.util.FcmUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Profile Photo Update Notification Service Implementation.
 * Sends silent FCM push to contacts when a user updates their profile photo.
 */
@Slf4j
@Service
public class ProfilePhotoUpdateServiceImpl implements ProfilePhotoUpdateService {

    private final FcmUtil fcmUtil;
    private final NotificationTokenRepository notificationTokenRepository;

    public ProfilePhotoUpdateServiceImpl(FcmUtil fcmUtil, NotificationTokenRepository notificationTokenRepository) {
        this.fcmUtil = fcmUtil;
        this.notificationTokenRepository = notificationTokenRepository;
    }

    @Override
    public void processProfilePhotoUpdateNotification(NotificationDTO notificationDTO) {
        log.info("=== Profile Photo Update Processing Started ===");
        log.info("Received profile photo update - customerId: {}, channel: {}",
                notificationDTO.getCustomerId(),
                notificationDTO.getChannel());

        try {
            if (!isValid(notificationDTO)) {
                log.error("Invalid profile photo update notification: {}", notificationDTO);
                return;
            }

            if (notificationDTO.getChannel() == NotificationChannel.INAPP) {
                sendProfilePhotoUpdatePush(notificationDTO);
            } else {
                log.warn("Unsupported channel for profile photo update: {}", notificationDTO.getChannel());
            }

            log.info("=== Profile Photo Update Processing Completed ===");
        } catch (Exception e) {
            log.error("Error processing profile photo update for customerId: {}. Error: {}",
                    notificationDTO.getCustomerId(), e.getMessage(), e);
        }
    }

    private void sendProfilePhotoUpdatePush(NotificationDTO notificationDTO) {
        String fcmToken = fetchFcmToken(notificationDTO.getCustomerId());
        if (fcmToken == null || fcmToken.isEmpty()) {
            log.error("FCM token not found for customerId: {}. Cannot send profile photo update push.",
                    notificationDTO.getCustomerId());
            return;
        }

        Map<String, String> fcmData = buildFcmDataMap(notificationDTO);

        try {
            String messageId = fcmUtil.sendDataOnlyPushNotification(fcmToken, fcmData, true);
            log.info("Profile photo update push sent successfully. MessageId: {}", messageId);
        } catch (Exception e) {
            log.error("Failed to send profile photo update push for customerId: {}. Error: {}",
                    notificationDTO.getCustomerId(), e.getMessage(), e);
        }
    }

    private Map<String, String> buildFcmDataMap(NotificationDTO notificationDTO) {
        Map<String, String> fcmData = new HashMap<>();

        fcmData.put("type", ApplicationConstants.FCM_NOTIFICATION_TYPE_PROFILE_PHOTO_UPDATE);
        fcmData.put("sound", ApplicationConstants.FCM_NOTIFICATION_SOUND_NONE);
        fcmData.put("badge", "0");

        if (notificationDTO.getSenderCustomerId() != null) {
            fcmData.put(ApplicationConstants.FCM_SENDER_CUSTOMER_ID_KEY, notificationDTO.getSenderCustomerId());
        }

        if (notificationDTO.getSenderMobile() != null) {
            fcmData.put(ApplicationConstants.FCM_SENDER_MOBILE_KEY, notificationDTO.getSenderMobile());
            fcmData.put("senderPhone", notificationDTO.getSenderMobile());
        }

        log.debug("Built FCM data map for profile photo update with {} entries", fcmData.size());
        return fcmData;
    }

    private boolean isValid(NotificationDTO notificationDTO) {
        if (notificationDTO == null) return false;
        if (notificationDTO.getCustomerId() == null) return false;
        if (notificationDTO.getChannel() == null) return false;
        return true;
    }

    private String fetchFcmToken(Long customerId) {
        try {
            Optional<NotificationToken> token = notificationTokenRepository.findFirstByCustomerId(customerId);
            if (token.isPresent() && token.get().getFcmToken() != null && !token.get().getFcmToken().isEmpty()) {
                return token.get().getFcmToken();
            }
            log.warn("No FCM token found for customerId: {}", customerId);
            return null;
        } catch (Exception e) {
            log.error("Error fetching FCM token for customerId: {}. Error: {}", customerId, e.getMessage(), e);
            return null;
        }
    }
}
