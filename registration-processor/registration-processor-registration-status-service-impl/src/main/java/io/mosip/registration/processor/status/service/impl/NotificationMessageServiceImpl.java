package io.mosip.registration.processor.status.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.registration.processor.status.entity.NotificationMessageEntity;
import io.mosip.registration.processor.status.repositary.NotificationMessageRepository;
import io.mosip.registration.processor.status.service.NotificationMessageService;

@Component
public class NotificationMessageServiceImpl implements NotificationMessageService {

	@Autowired
	private NotificationMessageRepository notificationMessageRepository;
	
	@Autowired
	ObjectMapper mapper;
	
	@Override
	public void saveNotificationDetails(String regId, Map<String, String> message) {
		NotificationMessageEntity entity = new NotificationMessageEntity();
		entity.setRegId(regId);
		
		try {
			String jsonString = mapper.writeValueAsString(message);
			entity.setNotificationMessage(jsonString);
		} catch (JsonProcessingException e) {
			
		}
		
		entity.setSentToOpencrvs(false);
		entity.setCreatedBy("SYSTEM");
		entity.setCreateDateTime(LocalDateTime.now(ZoneId.of("UTC")));
		
		notificationMessageRepository.save(entity);
	}
	
	@Override
	public Map<String, String> getNotificationDetails(String regId) {
		Optional<NotificationMessageEntity> entityOp = notificationMessageRepository.findById(regId);
		
		if (entityOp.isPresent()) {
			String json = entityOp.get().getNotificationMessage();
			try {
				Map<String, String> map = mapper.readValue(json, new TypeReference<Map<String, String>>() {});
				return map;
			} catch (JsonProcessingException e) {
				
			}
		}
		
		return null;
	}

	@Override
	public List<NotificationMessageEntity> getRecordsNotSentToOpencrvs(int fetchSize) {
		return notificationMessageRepository.getRecordsNotSentToOpencrvs(fetchSize);
	}

	@Override
	public void saveRecord(NotificationMessageEntity entity) {
		notificationMessageRepository.save(entity);
	}

}
