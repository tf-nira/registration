package io.mosip.registration.processor.status.service;

import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public interface NotificationMessageService {
	
	public void saveNotificationDetails(String regId, Map<String, String> message);
	
	public Map<String, String> getNotificationDetails(String regId);
}
