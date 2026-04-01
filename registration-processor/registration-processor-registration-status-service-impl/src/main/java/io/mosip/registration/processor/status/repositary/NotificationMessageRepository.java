package io.mosip.registration.processor.status.repositary;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.mosip.registration.processor.status.entity.NotificationMessageEntity;

public interface NotificationMessageRepository extends JpaRepository<NotificationMessageEntity, String> {

	@Query(value ="SELECT * FROM reg_notification_message r WHERE r.reg_id like '%-%' AND r.sent_to_opencrvs IS NOT TRUE order by r.cr_dtimes LIMIT :fetchSize", nativeQuery = true)
	public List<NotificationMessageEntity> getRecordsNotSentToOpencrvs(@Param("fetchSize") Integer fetchSize);
}
 