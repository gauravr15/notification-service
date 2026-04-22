package com.odin.notification.dto;

import java.sql.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationTokenDTO {

    private Long id;
    private Long customerId;
    private String fcmToken;
    private String deviceSignature;
    private Timestamp createTimestamp;
    private Timestamp updateTimestamp;

    /** Apple PushKit VoIP token (iOS only). Optional — not sent by Android clients. */
    private String voipToken;

    /**
     * Device platform type sent by the client on login/signup.
     * Expected values: "IOS" or "ANDROID".
     * Optional — absent for legacy clients; null is handled gracefully in the service.
     */
    private String deviceType;
}
