package com.odin.notification.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

import com.odin.notification.enums.NotificationChannel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "notification_message")
public class NotificationMessageModel {
	
	@Id
	@Column(name = "id")
	private long id;
	
	@Column(name = "message_id")
	private long messageId;
	
	@Column(name = "subject")
	private String subject;
	
	@Column(name = "message")
	private String message;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "channel")
	private NotificationChannel channel;

}