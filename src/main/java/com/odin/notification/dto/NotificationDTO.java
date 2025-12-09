package com.odin.notification.dto;

import java.util.Map;

import com.odin.notification.enums.NotificationChannel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationDTO {
    private Long customerId;
    private Long notificationId;
    private NotificationChannel channel;
    private Map<String, String> map;
    private String mobile;
    private String email;
}
