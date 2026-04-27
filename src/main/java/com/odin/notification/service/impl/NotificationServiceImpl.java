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
			NotificationToken obj = null;
			Optional<NotificationToken> exists = repo.findFirstByCustomerId(
					notificationServ.getCustomerId());
			if (exists.isPresent()) {
				obj = exists.get();
				obj.setFcmToken(notificationServ.getFcmToken());
				obj.setDeviceSignature(notificationServ.getDeviceSignature());
				// Preserve voipToken if the incoming DTO has one (e.g. iOS re-registration)
				if (notificationServ.getVoipToken() != null && !notificationServ.getVoipToken().isBlank()) {
					obj.setVoipToken(notificationServ.getVoipToken());
				}
				obj.setUpdateTimestamp(new java.sql.Timestamp(System.currentTimeMillis()));
			} else {
				obj = mapper.readValue(mapper.writeValueAsString(notificationServ), NotificationToken.class);
			}
			repo.save(obj);
			return responseObj.buildResponse(ResponseCodes.SUCCESS_CODE, obj);
		} catch (Exception e) {
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
