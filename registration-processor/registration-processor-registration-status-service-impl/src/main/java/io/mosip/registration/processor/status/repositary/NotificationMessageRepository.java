package io.mosip.registration.processor.status.repositary;

import org.springframework.data.jpa.repository.JpaRepository;

import io.mosip.registration.processor.status.entity.NotificationMessageEntity;

public interface NotificationMessageRepository extends JpaRepository<NotificationMessageEntity, String> {

}
 