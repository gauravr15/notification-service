package com.odin.notification.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event payload received from Kafka when a customer account is deleted.
 * Published by profile-service on the "account.deletion" topic.
 *
 * Fields mirror profile-service's AccountDeletionEvent exactly so Jackson
 * can deserialize without type-header resolution across service boundaries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionEvent {

    /** String representation of the deleted customer's numeric ID. */
    private String customerId;

    /** Global-pepper SHA-256 hash of the customer's phone number. */
    private String globalPhoneHash;

    /** Epoch-millis timestamp of when the deletion was processed. */
    private long timestamp;

    /**
     * CustomerIds of users who had the deleted user in their contacts.
     * notification-service fans out ACCOUNT_DELETED FCM to each of them.
     * May be empty (if the user had no registered contacts), never null.
     */
    @Builder.Default
    private List<String> contactOwnerIds = new ArrayList<>();
}
