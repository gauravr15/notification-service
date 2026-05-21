package com.odin.notification.controller;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.odin.notification.constants.ApplicationConstants;
import com.odin.notification.dto.AccountDeletionEvent;
import com.odin.notification.repo.NotificationTokenRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Kafka consumer for account deletion events.
 *
 * Listens to the "account.deletion" topic published by profile-service and
 * purges all FCM / VoIP tokens belonging to the deleted customer.
 *
 * Without this consumer the notification_tokens table would retain stale
 * tokens indefinitely, causing push-notification delivery attempts to a
 * non-existent user and potential privacy leakage.
 *
 * Idempotent: calling deleteByCustomerId for an already-removed customer
 * is a no-op at the DB level.
 */
@Slf4j
@Component
public class AccountDeletionConsumer {

    private final NotificationTokenRepository notificationTokenRepository;

    public AccountDeletionConsumer(NotificationTokenRepository notificationTokenRepository) {
        this.notificationTokenRepository = notificationTokenRepository;
    }

    @Transactional
    @KafkaListener(
            topics = ApplicationConstants.KAFKA_ACCOUNT_DELETION_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "accountDeletionListenerFactory"
    )
    public void onAccountDeleted(AccountDeletionEvent event) {
        log.info("[ACCOUNT-DELETION] Received account deletion event for customerId={}",
                event.getCustomerId());

        if (event.getCustomerId() == null || event.getCustomerId().isBlank()) {
            log.warn("[ACCOUNT-DELETION] Ignoring event with missing customerId");
            return;
        }

        try {
            Long customerIdLong = Long.parseLong(event.getCustomerId());
            notificationTokenRepository.deleteByCustomerId(customerIdLong);
            log.info("[ACCOUNT-DELETION] FCM/VoIP tokens purged for customerId={}", event.getCustomerId());
        } catch (NumberFormatException e) {
            log.error("[ACCOUNT-DELETION] Invalid customerId format='{}', skipping purge",
                    event.getCustomerId());
        } catch (Exception e) {
            log.error("[ACCOUNT-DELETION] Failed to purge tokens for customerId={}: {}",
                    event.getCustomerId(), e.getMessage());
            throw e; // allow Kafka to retry via container error handler
        }
    }
}
