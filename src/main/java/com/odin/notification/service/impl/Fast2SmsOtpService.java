package com.odin.notification.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.odin.notification.dto.Fast2SmsRequest;
import com.odin.notification.dto.Fast2SmsResponse;
import com.odin.notification.dto.NotificationDTO;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for sending OTP SMS via Fast2SMS gateway. Uses POST with JSON body
 * and route "q".
 */
@Slf4j
@Service
public class Fast2SmsOtpService {

	private final RestTemplate restTemplate;

	@Value("${fast2sms.api.url}")
	private String apiUrl;

	@Value("${fast2sms.authorization.key}")
	private String authorizationKey;

	@Value("${sms.delivery.enabled:false}")
	private boolean smsDeliveryEnabled;

	@Value("${fast2sms.sender-id}")
	private String senderId;

	public Fast2SmsOtpService() {
		this.restTemplate = new RestTemplate();
	}

	/**
	 * Send OTP SMS to the given phone number via Fast2SMS.
	 *
	 * @param phoneNumber recipient phone number (digits only)
	 * @param otp         the OTP value (digits only)
	 * @return true if the gateway returned success, false otherwise
	 */

	public boolean sendOtp(String phoneNumber, String otp, NotificationDTO notification) {
	    log.info("[SMS-OTP] Initiating DLT OTP SMS to number: {}", maskPhone(phoneNumber));

	    if (!smsDeliveryEnabled) {
	        log.info("[SMS-OTP] SMS delivery is DISABLED. Skipping: {}", maskPhone(phoneNumber));
	        return true;
	    }

	    try {
	        String messageId ="";
	        String dltTemplateId = "";

	        // Using the IDs explicitly from your provided CSV file
	        if (notification.getNotificationId() == 2021) {
	            messageId = "217848"; // Login
	            dltTemplateId = "1207178119651027157";
	        } else if(notification.getNotificationId() == 2020){
	            messageId = "217847"; // Signup
	            dltTemplateId = "1207178119211275573";
	        }

	        String url = String.format("%s?authorization=%s&route=dlt&sender_id=%s&message=%s&template_id=%s&variables_values=%s&numbers=%s",
	                apiUrl, authorizationKey, senderId, messageId, dltTemplateId, otp, phoneNumber);

	        log.info("[SMS-OTP] Sending DLT request to Fast2SMS. URL: {}", url);

	        // Using getForEntity to match the successful GET request structure
	        ResponseEntity<Fast2SmsResponse> response = restTemplate.getForEntity(url, Fast2SmsResponse.class);

	        Fast2SmsResponse responseBody = response.getBody();
	        
	        return response.getStatusCode().is2xxSuccessful() && responseBody != null && responseBody.isSuccess();

	    } catch (Exception e) {
	        log.error("[SMS-OTP] Failed to send DLT SMS. Error: {}", e.getMessage(), e);
	        return false;
	    }
	}

	/**
	 * Mask phone number for safe logging (show last 4 digits only).
	 */
	private String maskPhone(String phone) {
		if (phone == null || phone.length() <= 4)
			return "****";
		return "****" + phone.substring(phone.length() - 4);
	}
}
