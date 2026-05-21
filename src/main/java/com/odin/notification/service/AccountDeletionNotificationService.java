package com.odin.notification.service;

import com.odin.notification.dto.AccountDeletionEvent;

/**
 * Service for handling account deletion notifications.
 *
 * Responsibilities:
 *  1. Purge FCM / VoIP tokens for the deleted user.
 *  2. Fan-out silent ACCOUNT_DELETED FCM to every user who had the deleted
 *     account saved as a contact, so their device clears cached photo / metadata.
 */
public interface AccountDeletionNotificationService {

    void processAccountDeletion(AccountDeletionEvent event);
}
