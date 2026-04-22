package com.odin.notification.controller;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.odin.notification.constants.ApplicationConstants;
import com.odin.notification.dto.NotificationTokenDTO;
import com.odin.notification.dto.ResponseDTO;
import com.odin.notification.service.NotificationService;

import java.util.Map;


@RestController
@RequestMapping(ApplicationConstants.API_VERSION)
public class NotificationController {
	
	
	@Autowired
	private NotificationService notification;
	
	@PostMapping(ApplicationConstants.FCM_NOTIFICATION_TOKEN+ApplicationConstants.SAVE)
	public ResponseEntity<Object> signUp(HttpServletRequest req,@Valid @RequestBody NotificationTokenDTO notificationServ){
		String deviceSignature = req.getHeader("deviceSignature");
		notificationServ.setDeviceSignature(deviceSignature);
		ResponseDTO response = notification.save(notificationServ);
		return new ResponseEntity<>(response,HttpStatus.OK);
	}

	/**
	 * POST /v1/fcmNotificationToken/voipToken
	 *
	 * Saves or updates the Apple PushKit VoIP token for an iOS device.
	 * Called by the Flutter app once the PKPushRegistryDelegate fires.
	 *
	 * Expected body: { "customerId": 123, "voipToken": "<hex token>" }
	 * Optional header: deviceSignature — used to match the exact device row.
	 */
	@PostMapping(ApplicationConstants.FCM_NOTIFICATION_TOKEN + ApplicationConstants.VOIP_TOKEN)
	public ResponseEntity<Object> saveVoipToken(HttpServletRequest req,
			@RequestBody Map<String, Object> body) {
		String deviceSignature = req.getHeader("deviceSignature");
		Long customerId = null;
		String voipToken = null;
		try {
			Object cid = body.get("customerId");
			customerId = cid instanceof Number ? ((Number) cid).longValue() : Long.parseLong(cid.toString());
			Object vt = body.get("voipToken");
			voipToken = vt != null ? vt.toString() : null;
		} catch (Exception e) {
			// malformed body — return failure
		}
		ResponseDTO response = notification.saveVoipToken(customerId, voipToken, deviceSignature);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
}

