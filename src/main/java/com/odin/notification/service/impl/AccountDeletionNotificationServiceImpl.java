package com.odin.notification.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.odin.notification.constants.ApplicationConstants;
import com.odin.notification.dto.AccountDeletionEvent;
import com.odin.notification.entity.NotificationToken;
import com.odin.notification.repo.NotificationTokenRepository;
import com.odin.notification.service.AccountDeletionNotificationService;
import com.odin.notification.util.FcmUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles all notification-side work triggered by an account deletion event:
 *
 *  1. Purge the deleted user's own FCM/VoIP tokens from notification_tokens.
 *  2. Fan-out a silent ACCOUNT_DELETED FCM push to every user who had the
 *     deleted account saved in their contacts.  This triggers Flutter's
 *     _handleAccountDeleted() which clears the local photo cache, removes the
 *     contactsBox entry, and evicts the customerMappingBox entry — so the
 *     receiver's home screen stops showing the deleted user's photo.
 *
 * Fan-out failures are logged but never rethrown so one bad token cannot abort
 * the entire delivery loop.
 */
@Slf4j
@Service
public class AccountDeletionNotificationServiceImpl implements AccountDeletionNotificationService {

    /** FCM data-map key that the Flutter router uses to dispatch the event. */
    private static final String FCM_TYPE_ACCOUNT_DELETED = "ACCOUNT_DELETED";

    private final NotificationTokenRepository notificationTokenRepository;
    private final FcmUtil fcmUtil;

    public AccountDeletionNotificationServiceImpl(
            NotificationTokenRepository notificationTokenRepository,
            FcmUtil fcmUtil) {
        this.notificationTokenRepository = notificationTokenRepository;
        this.fcmUtil = fcmUtil;
    }

    @Override
    @Transactional
    public void processAccountDeletion(AccountDeletionEvent event) {
        if (event == null || event.getCustomerId() == null || event.getCustomerId().isBlank()) {
            log.warn("[ACCOUNT-DELETION] Received null or empty event, skipping");
            return;
        }

        String deletedCustomerId = event.getCustomerId();
        log.info("[ACCOUNT-DELETION] Processing deletion for customerId={}", deletedCustomerId);

        // --- 1. Purge deleted user's own FCM tokens ---
        try {
            Long customerIdLong = Long.parseLong(deletedCustomerId);
            notificationTokenRepository.deleteByCustomerId(customerIdLong);
            log.info("[ACCOUNT-DELETION] FCM/VoIP tokens purged for customerId={}", deletedCustomerId);
        } catch (NumberFormatException e) {
            log.error("[ACCOUNT-DELETION] Invalid customerId format='{}', cannot purge tokens", deletedCustomerId);
        }

        // --- 2. Fan-out ACCOUNT_DELETED FCM to each contact owner ---
        List<String> contactOwnerIds = event.getContactOwnerIds();
        if (contactOwnerIds == null || contactOwnerIds.isEmpty()) {
            log.info("[ACCOUNT-DELETION] No contact owners to notify for customerId={}", deletedCustomerId);
            return;
        }

        log.info("[ACCOUNT-DELETION] Notifying {} contact owner(s) for customerId={}",
                contactOwnerIds.size(), deletedCustomerId);

        Map<String, String> fcmData = buildAccountDeletedFcmPayload(deletedCustomerId);
        int successCount = 0;
        int failCount = 0;

        for (String ownerUserId : contactOwnerIds) {
            try {
                Long ownerIdLong = Long.parseLong(ownerUserId);
                Optional<NotificationToken> tokenOpt =
                        notificationTokenRepository.findFirstByCustomerId(ownerIdLong);

                if (tokenOpt.isEmpty() || tokenOpt.get().getFcmToken() == null
                        || tokenOpt.get().getFcmToken().isBlank()) {
                    log.debug("[ACCOUNT-DELETION] No FCM token for ownerUserId={}, skipping", ownerUserId);
                    continue;
                }

                fcmUtil.sendDataOnlyPushNotification(tokenOpt.get().getFcmToken(), fcmData, true);
                successCount++;
                log.debug("[ACCOUNT-DELETION] ACCOUNT_DELETED FCM sent to ownerUserId={}", ownerUserId);

            } catch (NumberFormatException e) {
                log.warn("[ACCOUNT-DELETION] Invalid ownerUserId format='{}', skipping", ownerUserId);
                failCount++;
            } catch (FirebaseMessagingException e) {
                log.warn("[ACCOUNT-DELETION] FCM delivery failed for ownerUserId={}: {}", ownerUserId, e.getMessage());
                failCount++;
            } catch (Exception e) {
                log.warn("[ACCOUNT-DELETION] Unexpected error notifying ownerUserId={}: {}", ownerUserId, e.getMessage());
                failCount++;
            }
        }

        log.info("[ACCOUNT-DELETION] Fan-out complete for customerId={} — sent={} failed={}",
                deletedCustomerId, successCount, failCount);
    }

    /**
     * Builds the FCM data-only payload for the ACCOUNT_DELETED signal.
     *
     * Flutter router matches on "type" == "ACCOUNT_DELETED" (kTypeAccountDeleted).
     * "deletedCustomerId" is read by _handleAccountDeleted() to identify the peer.
     */
    private Map<String, String> buildAccountDeletedFcmPayload(String deletedCustomerId) {
        Map<String, String> data = new HashMap<>();
        data.put("type", FCM_TYPE_ACCOUNT_DELETED);
        data.put("deletedCustomerId", deletedCustomerId);
        data.put("sound", ApplicationConstants.FCM_NOTIFICATION_SOUND_NONE);
        data.put("badge", "0");
        return data;
    }
}
