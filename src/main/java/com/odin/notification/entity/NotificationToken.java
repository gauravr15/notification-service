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
}
