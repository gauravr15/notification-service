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
}
