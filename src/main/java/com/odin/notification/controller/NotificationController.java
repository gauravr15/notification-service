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
}

