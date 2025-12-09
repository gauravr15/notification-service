package com.odin.notification.service.impl;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.mail.internet.MimeMessage;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.mailersend.sdk.MailerSend;
import com.mailersend.sdk.MailerSendResponse;
import com.mailersend.sdk.emails.Email;
import com.odin.notification.constants.ResponseCodes;
import com.odin.notification.dto.EmailDTO;
import com.odin.notification.dto.ResponseDTO;
import com.odin.notification.entity.NotificationMessageModel;
import com.odin.notification.repo.NotificationMessageRepository;
import com.odin.notification.service.EmailService;
import com.odin.notification.util.MessageLoggerUtility;
import com.odin.notification.util.ResponseObject;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

	@Value("${notification.html.enabled}")
	private boolean isHtmlEnabled;

	@Value("${spring.mail.username}")
	private String fromEmail;

	@Value("${admin.email}")
	private String adminMail;

	@Value("${send.admin.mail}")
	private boolean sendAdminMail;

	@Value("${email.api.key}")
	private String apikey;
	
	@Value("${email.brand.name}")
	private String brandName;
	
	@Autowired
	private JavaMailSender mailSender;

	@Autowired
	private ResponseObject builder;

	@Autowired
	private NotificationMessageRepository notifRepo;
	
	@Autowired
	private MessageLoggerUtility notifLoggerRepo;

	@Autowired
	private Configuration freemarkerConfig;

	@Override
	public ResponseDTO sendEmailRest(EmailDTO emailDTO) {
	    try {
	        // Fetch the template message from DB
	        NotificationMessageModel notifMessage = notifRepo.findByMessageId(emailDTO.getMessageId());
	        if (notifMessage == null) {
	        	log.error("Unable to fetch message with id : {}", emailDTO.getMessageId());
	            return builder.buildResponse(ResponseCodes.NO_DATA_FOUND);
	        }
	        log.info("Message fetched with message id : {}", notifMessage);

	        // Prepare the template model
	        Map<String, Object> model = new HashMap<>();
	        model.put("name", emailDTO.getName());
	        model.put("body", emailDTO.getBody());

	        // Process the FreeMarker template
	        Template template = new Template("dbTemplate", new StringReader(notifMessage.getMessage()), freemarkerConfig);
	        StringWriter writer = new StringWriter();
	        template.process(model, writer);
	        String htmlContent = writer.toString();

	        // Create MailerSend email
	        Email email = new Email();
	        email.setFrom(brandName, fromEmail); 
	        email.addRecipient(emailDTO.getName(), emailDTO.getToEmail()); 
	        email.AddCc(brandName, fromEmail); 
	        email.setSubject(notifMessage.getSubject() != null ? notifMessage.getSubject() : emailDTO.getSubject());
	        email.setHtml(htmlContent); 

	        MailerSend ms = new MailerSend();
	        ms.setToken(apikey);
	        MailerSendResponse response = ms.emails().send(email);

	        log.info("Email sent successfully, messageId: {}", response.messageId);
	        notifLoggerRepo.saveSentMessage(emailDTO, response, email);
	        return builder.buildResponse(ResponseCodes.SUCCESS);

	    } catch (Exception e) {
	        log.error("Error sending email to {}: {}", emailDTO.getToEmail(), ExceptionUtils.getStackTrace(e));
	        return builder.buildResponse(ResponseCodes.FAILURE);
	    }
	}


	@Override
	public ResponseDTO sendEmail(EmailDTO email) {
		try {
			NotificationMessageModel notifMessage = notifRepo.findByMessageId(email.getMessageId());

			if (notifMessage == null) {
				return builder.buildResponse(ResponseCodes.NO_DATA_FOUND);
			}
			log.info("message fetched with message id : {}", notifMessage);

			Map<String, Object> model = new HashMap<>();
			model.put("name", email.getName());
			model.put("body", email.getBody());

			Template template = new Template("dbTemplate", new StringReader(notifMessage.getMessage()),
					freemarkerConfig);

			StringWriter writer = new StringWriter();
			template.process(model, writer);
			String htmlContent = writer.toString();

			MimeMessage mimeMessage = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
			log.info("message sent from : {}, to : {}, using channel: {}", fromEmail, email.getToEmail(),
					email.getChannel().name());
			log.info("message sent is : {}", htmlContent);
			helper.setFrom(fromEmail);
			helper.setTo(email.getToEmail());
			helper.setSubject(notifMessage.getSubject() != null ? notifMessage.getSubject() : email.getSubject());
			helper.setText(htmlContent, isHtmlEnabled);

			mailSender.send(mimeMessage);

			if (sendAdminMail) {
				log.info("message sent successfully, sending admin mail");
				model.put("name", "admin");
				helper.setTo(adminMail);
				helper.setSubject(notifMessage.getSubject() != null
						? notifMessage.getSubject().concat(" | ")
								.concat(email.getToEmail().concat(" | ").concat(email.getMobile()))
						: email.getSubject().concat(" | ")
								.concat(email.getToEmail().concat(" | ").concat(email.getMobile())));

				mailSender.send(mimeMessage);
			}
			return builder.buildResponse(ResponseCodes.SUCCESS);
		} catch (Exception e) {
			log.error("Error occured while performing send notification dto: {}, exception: {}", email,
					ExceptionUtils.getStackTrace(e));
			return builder.buildResponse(ResponseCodes.FAILURE);
		}
	}
}
