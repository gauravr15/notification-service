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
			Optional<NotificationToken> exists = repo.findFirstByCustomerIdAndDeviceSignature(
					notificationServ.getCustomerId(), notificationServ.getDeviceSignature());
			if (exists.isPresent()) {
				obj = exists.get();
				obj.setFcmToken(notificationServ.getFcmToken());
			} else {
				obj = mapper.readValue(mapper.writeValueAsString(notificationServ), NotificationToken.class);
			}
			repo.save(obj);
			return responseObj.buildResponse(ResponseCodes.SUCCESS_CODE, obj);
		} catch (Exception e) {
			return responseObj.buildResponse(ResponseCodes.FAILURE_CODE);
		}
	}

}
