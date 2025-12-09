package com.odin.notification.service;

import com.odin.notification.dto.NotificationTokenDTO;
import com.odin.notification.dto.ResponseDTO;

public interface NotificationService {

	ResponseDTO save(NotificationTokenDTO notificationServ);

}
