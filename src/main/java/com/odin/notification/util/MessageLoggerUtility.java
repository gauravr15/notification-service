package com.odin.notification.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mailersend.sdk.MailerSendResponse;
import com.mailersend.sdk.emails.Email;
import com.odin.notification.dto.EmailDTO;
import com.odin.notification.entity.NotificationMessageLogger;
import com.odin.notification.repo.NotificationMessageLoggerRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MessageLoggerUtility {

	@Autowired
	private NotificationMessageLoggerRepository logger;

	public boolean saveSentMessage(EmailDTO email, MailerSendResponse resp, Email reqEmail) {
		try {
			NotificationMessageLogger dto = NotificationMessageLogger.builder().messageId(resp.messageId)
					.channel(email.getChannel()).message(reqEmail.html).toEmail(email.getToEmail())
					.mobile(email.getMobile()).responseStatusCode(resp.responseStatusCode)
					.status(resp.responseStatusCode == 202 ? "SUCCESS" : "FAILURE").build();
			logger.save(dto);
			return true;
		} catch (Exception e) {
			log.error("Failed to log message ");
			return false;
		}
	}
}
