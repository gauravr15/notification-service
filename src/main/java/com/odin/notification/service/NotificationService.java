package com.odin.notification.service;

import com.odin.notification.dto.NotificationTokenDTO;
import com.odin.notification.dto.ResponseDTO;

public interface NotificationService {

	ResponseDTO save(NotificationTokenDTO notificationServ);

	/**
	 * Save or update the Apple VoIP token for a customer's iOS device.
	 * Used to deliver CALL_INVITE notifications as APNs VoIP pushes (Phase 3).
	 *
	 * @param customerId      the customer whose token to update
	 * @param voipToken       the PushKit VoIP APNs token hex string
	 * @param deviceSignature optional device fingerprint for multi-device disambiguation
	 * @return ResponseDTO with result status
	 */
	ResponseDTO saveVoipToken(Long customerId, String voipToken, String deviceSignature);

}
