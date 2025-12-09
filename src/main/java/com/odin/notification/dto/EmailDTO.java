package com.odin.notification.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

import com.odin.notification.enums.NotificationChannel;
import com.sun.istack.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmailDTO {
	
	@NotNull
	@NotEmpty
	@NotBlank
	private long messageId;

	private String toEmail;
	
	private String mobile;
	
	private String subject;
	
	private String body;
	
	private String name;
	
	private NotificationChannel channel;

}
