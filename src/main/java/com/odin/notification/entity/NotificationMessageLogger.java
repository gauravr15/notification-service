package com.odin.notification.entity;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import com.odin.notification.enums.NotificationChannel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@Table(name = "notification_message_logger")
@AllArgsConstructor
@NoArgsConstructor
public class NotificationMessageLogger {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;
	
	@Column(name = "message_id")
	private String messageId;
	
	@Column(name = "message")
	private String message;
	
	@Column(name = "channel")
	@Enumerated(EnumType.STRING)
	private NotificationChannel channel;
	
	@Column(name = "to_email")
	private String toEmail;
	
	@Column(name = "mobile")
	private String mobile;
	
	@Column(name = "status_code")
	private int responseStatusCode;
	
	@Column(name = "status")
	private String status;
	 
	@CreationTimestamp
	@Column(name = "sent_date_time")
	private Timestamp sentDateTime;
	

}
