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
import com.odin.notification.dto.EmailDTO;
import com.odin.notification.dto.ResponseDTO;
import com.odin.notification.service.EmailService;

@RestController
@RequestMapping(ApplicationConstants.API_VERSION)
public class EmailController {
	
	@Autowired
	private EmailService service;
	
	@PostMapping(ApplicationConstants.EMAIL+ApplicationConstants.SEND)
	public ResponseEntity<Object> sendEmail(HttpServletRequest req,@Valid @RequestBody EmailDTO email){
		ResponseDTO response = service.sendEmail(email);
		return new ResponseEntity<>(response,HttpStatus.OK);
		
	}
	
	@PostMapping(ApplicationConstants.EMAIL+ApplicationConstants.SEND+ApplicationConstants.REST)
	public ResponseEntity<Object> sendEmailRest(HttpServletRequest req,@Valid @RequestBody EmailDTO email){
		ResponseDTO response = service.sendEmailRest(email);
		return new ResponseEntity<>(response,HttpStatus.OK);
		
	}

}
