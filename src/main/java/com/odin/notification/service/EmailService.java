package com.odin.notification.service;

import com.odin.notification.dto.EmailDTO;
import com.odin.notification.dto.ResponseDTO;

public interface EmailService {

	ResponseDTO sendEmail(EmailDTO email);

	ResponseDTO sendEmailRest(EmailDTO emailDTO);

}
