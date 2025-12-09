package com.odin.notification.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.odin.notification.entity.NotificationMessageLogger;

public interface NotificationMessageLoggerRepository extends JpaRepository<NotificationMessageLogger, Long>{

}
