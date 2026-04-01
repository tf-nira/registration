package io.mosip.registration.processor.status.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import io.mosip.registration.processor.status.entity.NotificationMessageEntity;

@Service
public interface NotificationMessageService {
	
	public void saveNotificationDetails(String regId, Map<String, String> message);
	
	public Map<String, String> getNotificationDetails(String regId);
	
	public List<NotificationMessageEntity> getRecordsNotSentToOpencrvs(int fetchSize);
	
	public void saveRecord(NotificationMessageEntity entity);
}
