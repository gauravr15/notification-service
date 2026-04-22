package com.odin.notification.entity;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "notification_token")
public class NotificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(nullable = true, length = 500, name = "fcm_token")
    private String fcmToken;

    @CreationTimestamp
    @Column(nullable = false, name = "create_timestamp")
    private Timestamp createTimestamp;

    @Column(name = "update_timestamp")
    private Timestamp updateTimestamp;
    
    @Column(nullable = true, length = 200, name = "device_signature")
    private String deviceSignature;

    /**
     * Apple PushKit VoIP token for iOS CallKit delivery.
     * When present, CALL_INVITE notifications are sent via APNs VoIP push
     * (apns-push-type: voip) instead of FCM, enabling reliable wake-up of
     * killed iOS apps and native CallKit incoming-call UI.
     */
    @Column(nullable = true, length = 200, name = "voip_token")
    private String voipToken;

    /**
     * Device platform type — "IOS" or "ANDROID".
     * Sent by the client on login/signup via the /save endpoint.
     * Used as the primary signal for routing: when a user switches devices
     * (e.g. iPhone → Android) this column is updated on the next login so the
     * backend knows to use FCM rather than APNs VoIP push, without needing a
     * separate DB query to check token availability at notification time.
     * Nullable for legacy rows that pre-date this column.
     */
    @Column(nullable = true, length = 10, name = "device_type")
    private String deviceType;
}
