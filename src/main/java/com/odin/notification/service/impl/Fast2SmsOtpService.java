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

import lombok.extern.slf4j.Slf4j;

/**
 * Service for sending OTP SMS via Fast2SMS gateway.
 * Uses POST with JSON body and route "q".
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
    public boolean sendOtp(String phoneNumber, String otp) {
        log.info("[SMS-OTP] Initiating OTP SMS to number: {}, otp length: {}",
                maskPhone(phoneNumber), otp.length());

        if (!smsDeliveryEnabled) {
            log.info("[SMS-OTP] SMS delivery is DISABLED (sms.delivery.enabled=false). Skipping actual send for number: {}", maskPhone(phoneNumber));
            return true;
        }

        try {
            Fast2SmsRequest body = Fast2SmsRequest.builder()
                    .route("q")
                    .message(otp)
                    .flash(0)
                    .numbers(phoneNumber)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authorization", authorizationKey);
            headers.set("cache-control", "no-cache");

            HttpEntity<Fast2SmsRequest> request = new HttpEntity<>(body, headers);

            log.info("[SMS-OTP] Sending POST to Fast2SMS for number: {}", maskPhone(phoneNumber));

            ResponseEntity<Fast2SmsResponse> response = restTemplate.postForEntity(
                    apiUrl, request, Fast2SmsResponse.class);

            Fast2SmsResponse responseBody = response.getBody();

            log.info("[SMS-OTP] Fast2SMS response — httpStatus: {}, return: {}, requestId: {}, statusCode: {}, message: {}",
                    response.getStatusCode(),
                    responseBody != null ? responseBody.isSuccess() : "null",
                    responseBody != null ? responseBody.getRequestId() : "null",
                    responseBody != null ? responseBody.getStatusCode() : "null",
                    responseBody != null ? responseBody.getMessage() : "null");

            return response.getStatusCode().is2xxSuccessful()
                    && responseBody != null
                    && responseBody.isSuccess();

        } catch (Exception e) {
            log.error("[SMS-OTP] Failed to send OTP SMS to number: {}. Error: {}",
                    maskPhone(phoneNumber), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Mask phone number for safe logging (show last 4 digits only).
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) return "****";
        return "****" + phone.substring(phone.length() - 4);
    }
}
