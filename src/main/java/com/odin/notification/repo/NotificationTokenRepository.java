package com.odin.notification.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.odin.notification.entity.NotificationToken;

public interface NotificationTokenRepository extends JpaRepository<NotificationToken, Long> {
	
	Optional<NotificationToken> findFirstByCustomerId(Long customerId);

	Optional<NotificationToken> findFirstByCustomerIdAndDeviceSignature(Long customerId, String deviceSignature);
	
}