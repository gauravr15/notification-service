package com.odin.notification.service.impl;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.notification.constants.ResponseCodes;
import com.odin.notification.dto.NotificationTokenDTO;
import com.odin.notification.dto.ResponseDTO;
import com.odin.notification.entity.NotificationToken;
import com.odin.notification.repo.NotificationTokenRepository;
import com.odin.notification.service.NotificationService;
import com.odin.notification.util.ResponseObject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

	@Autowired
	private NotificationTokenRepository repo;

	@Autowired
	private ResponseObject responseObj;

	@Autowired
	private ObjectMapper mapper;

	@Override
	public ResponseDTO save(NotificationTokenDTO notificationServ) {
		try {
			if (notificationServ == null) {
				log.warn("[NotificationService] save() called with null DTO — skipping");
				return responseObj.buildResponse(ResponseCodes.FAILURE_CODE);
			}
			log.info("[NotificationService] save() invoked — customerId={} deviceType={} hasVoipToken={}",
					notificationServ.getCustomerId(),
					notificationServ.getDeviceType(),
					notificationServ.getVoipToken() != null && !notificationServ.getVoipToken().isBlank());

			NotificationToken obj = null;
			Optional<NotificationToken> exists = repo.findFirstByCustomerId(
					notificationServ.getCustomerId());
			if (exists.isPresent()) {
				obj = exists.get();
				obj.setFcmToken(notificationServ.getFcmToken());
				obj.setDeviceSignature(notificationServ.getDeviceSignature());

				// Update deviceType when the client sends it (login/signup).
				// This handles the iOS→Android or Android→iOS switch scenario:
				// the next login from the new device overwrites the column so
				// push routing stays correct without an extra DB look-up.
				if (notificationServ.getDeviceType() != null && !notificationServ.getDeviceType().isBlank()) {
					String prevDeviceType = obj.getDeviceType();
					obj.setDeviceType(notificationServ.getDeviceType().toUpperCase());
					if (!notificationServ.getDeviceType().toUpperCase().equals(prevDeviceType)) {
						log.info("[NotificationService] device_type changed: prev={} new={} customerId={}",
								prevDeviceType, obj.getDeviceType(), notificationServ.getCustomerId());
						// If the user switched to Android, clear the stale VoIP token so
						// APNs pushes are not attempted for an Android device.
						if ("ANDROID".equalsIgnoreCase(obj.getDeviceType())) {
							log.info("[NotificationService] Clearing voipToken for Android device — customerId={}",
									notificationServ.getCustomerId());
							obj.setVoipToken(null);
						}
					}
				}

				// Preserve / update voipToken only for iOS devices.
				// Guard: never set a VoIP token on a row that is now Android.
				if (notificationServ.getVoipToken() != null && !notificationServ.getVoipToken().isBlank()) {
					if ("ANDROID".equalsIgnoreCase(obj.getDeviceType())) {
						log.warn("[NotificationService] Ignoring voipToken in save() for Android device — customerId={}",
								notificationServ.getCustomerId());
					} else {
						obj.setVoipToken(notificationServ.getVoipToken());
					}
				}

				obj.setUpdateTimestamp(new java.sql.Timestamp(System.currentTimeMillis()));
			} else {
				obj = mapper.readValue(mapper.writeValueAsString(notificationServ), NotificationToken.class);
				if (obj.getDeviceType() != null) {
					obj.setDeviceType(obj.getDeviceType().toUpperCase());
				}
				log.info("[NotificationService] New token row created — customerId={} deviceType={}",
						obj.getCustomerId(), obj.getDeviceType());
			}
			repo.save(obj);
			return responseObj.buildResponse(ResponseCodes.SUCCESS_CODE, obj);
		} catch (Exception e) {
			log.error("[NotificationService] save() failed — customerId={} error={}",
					notificationServ != null ? notificationServ.getCustomerId() : "null", e.getMessage(), e);
			return responseObj.buildResponse(ResponseCodes.FAILURE_CODE);
		}
	}

	@Override
	public ResponseDTO saveVoipToken(Long customerId, String voipToken, String deviceSignature) {
		if (customerId == null || voipToken == null || voipToken.isBlank()) {
			return responseObj.buildResponse(ResponseCodes.FAILURE_CODE);
		}
		try {
			NotificationToken obj;
			Optional<NotificationToken> byDevice = deviceSignature != null && !deviceSignature.isBlank()
					? repo.findFirstByCustomerIdAndDeviceSignature(customerId, deviceSignature)
					: Optional.empty();
			if (byDevice.isPresent()) {
				obj = byDevice.get();
			} else {
				Optional<NotificationToken> any = repo.findFirstByCustomerId(customerId);
				if (any.isPresent()) {
					obj = any.get();
				} else {
					obj = new NotificationToken();
					obj.setCustomerId(customerId);
					if (deviceSignature != null && !deviceSignature.isBlank()) {
						obj.setDeviceSignature(deviceSignature);
					}
				}
			}
			obj.setVoipToken(voipToken);
			obj.setUpdateTimestamp(new java.sql.Timestamp(System.currentTimeMillis()));
			repo.save(obj);
			return responseObj.buildResponse(ResponseCodes.SUCCESS_CODE, obj);
		} catch (Exception e) {
			return responseObj.buildResponse(ResponseCodes.FAILURE_CODE);
		}
	}

}
