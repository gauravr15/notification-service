package com.odin.notification.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.odin.notification.entity.NotificationMessageModel;

public interface NotificationMessageRepository extends JpaRepository<NotificationMessageModel, Long>{

	NotificationMessageModel findByMessageId(long messageId);

}
