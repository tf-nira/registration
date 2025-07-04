package io.mosip.registration.processor.notification.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.packet.storage.utils.IdSchemaUtil;
import io.mosip.registration.processor.packet.storage.utils.PacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.status.dao.RegistrationStatusDao;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.dto.TransactionDto;
import io.mosip.registration.processor.status.entity.BaseRegistrationPKEntity;
import io.mosip.registration.processor.status.entity.RegistrationStatusEntity;
import io.mosip.registration.processor.status.service.AnonymousProfileService;
import io.mosip.registration.processor.status.service.RegistrationStatusService;
import io.mosip.registration.processor.status.service.TransactionService;

@Component
public class AnonymousProfileScheduler {
	
	private static Logger regProcLogger = RegProcessorLogger.getLogger(AnonymousProfileScheduler.class);
	private static final String USER = "MOSIP_SYSTEM";
	
	@Value("${mosip.anonymous.profile.scheduler.fetchsize:5}")
	private Integer fetchSize;
	
	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;
	
	@Autowired
	TransactionService<TransactionDto> transactionService;
	
	@Autowired
	private RegistrationStatusDao registrationStatusDao;
	
	@Autowired
	private Utilities utility;
	
	@Autowired
	private IdSchemaUtil idSchemaUtil;

	@Autowired
	private PacketManagerService packetManagerService;
	
	@Autowired
	private AnonymousProfileService anonymousProfileService;
	
	@Scheduled(cron = "${mosip.anonymous.profile.scheduler.cron.expression:0 0/3 * * * ?}")
	public void addAnonymousprofile() {
		regProcLogger.info("Batch job for anonymous profile started");
		List<InternalRegistrationStatusDto> packets = registrationStatusService.getAnonymousNotAddedPackets(fetchSize);
		
		regProcLogger.info("Records picked for adding anonymous profile: " + packets.size());
		
		AtomicInteger profileAdded = new AtomicInteger(0);
		
		packets.forEach(packet -> {
			try {
				String json = null;
				String registrationId = packet.getRegistrationId();
				String registrationType = packet.getRegistrationType();

				regProcLogger.info("Adding anonymous profile for registration id {}", registrationId);

//				InternalRegistrationStatusDto registrationStatusDto = registrationStatusService.getRegistrationStatus(
//						registrationId, registrationType, packet.getIteration(),
//						packet.getWorkflowInstanceId());
				JSONObject regProcessorIdentityJson = utility.getRegistrationProcessorMappingJson(MappingJsonConstants.IDENTITY);
				String idSchemaVersionValue = JsonUtil.getJSONValue(JsonUtil.getJSONObject(regProcessorIdentityJson, MappingJsonConstants.IDSCHEMA_VERSION), MappingJsonConstants.VALUE);
				String schemaVersion = packetManagerService.getFieldByMappingJsonKey(registrationId,
						idSchemaVersionValue, registrationType, ProviderStageName.WORKFLOW_MANAGER);
				Map<String,String> fieldTypeMap = idSchemaUtil.getIdSchemaFieldTypes(
						Double.parseDouble(schemaVersion));
				Map<String, String> fieldMap = packetManagerService.getFields(registrationId,
						idSchemaUtil.getDefaultFields(Double.valueOf(schemaVersion)), registrationType,
						ProviderStageName.WORKFLOW_MANAGER);
				Map<String, String> metaInfoMap = packetManagerService.getMetaInfo(registrationId, registrationType,
						ProviderStageName.WORKFLOW_MANAGER);
				BiometricRecord biometricRecord = packetManagerService.getBiometrics(registrationId,
						MappingJsonConstants.INDIVIDUAL_BIOMETRICS, registrationType, ProviderStageName.WORKFLOW_MANAGER);
				json = anonymousProfileService.buildJsonStringFromPacketInfo(biometricRecord, fieldMap, fieldTypeMap,
						metaInfoMap, packet.getStatusCode(), packet.getRegistrationStageName());
				anonymousProfileService.saveAnonymousProfile(registrationId, packet.getRegistrationStageName(), json);
				
				regProcLogger.info("added anonymous profile for registration id {}", registrationId);
				
				profileAdded.incrementAndGet();
				packet.setIsAnonymousProfileAdded(true);
				packet.setUpdatedBy(USER);
				packet.setUpdateDateTime(LocalDateTime.now(ZoneId.of("UTC")));
				RegistrationStatusEntity entity = convertDtoToEntity(packet);
				registrationStatusDao.save(entity);
			} catch (Exception e) {
				regProcLogger.error("Failed to add anonymous profile: " + e.getMessage() , e);
			}
		});
		regProcLogger.info("Batch job completed, profile added: " + profileAdded.get());
	}
	
	private RegistrationStatusEntity convertDtoToEntity(InternalRegistrationStatusDto dto) {
		BaseRegistrationPKEntity pk = new BaseRegistrationPKEntity();
		pk.setWorkflowInstanceId(dto.getWorkflowInstanceId());

		RegistrationStatusEntity registrationStatusEntity = new RegistrationStatusEntity();
		registrationStatusEntity.setId(pk);
		registrationStatusEntity.setRegId(dto.getRegistrationId());
		registrationStatusEntity.setRegistrationType(dto.getRegistrationType());
		registrationStatusEntity.setIteration(dto.getIteration());
		registrationStatusEntity.setReferenceRegistrationId(dto.getReferenceRegistrationId());
		registrationStatusEntity.setStatusCode(dto.getStatusCode());
		registrationStatusEntity.setLangCode(dto.getLangCode());
		registrationStatusEntity.setStatusComment(dto.getStatusComment());
		registrationStatusEntity.setLatestRegistrationTransactionId(dto.getLatestRegistrationTransactionId());
		registrationStatusEntity.setIsActive(dto.isActive());
		registrationStatusEntity.setCreatedBy(dto.getCreatedBy());
		if (dto.getCreateDateTime() == null) {
			registrationStatusEntity.setCreateDateTime(LocalDateTime.now(ZoneId.of("UTC")));
		} else {
			registrationStatusEntity.setCreateDateTime(dto.getCreateDateTime());
		}
		registrationStatusEntity.setUpdatedBy(dto.getUpdatedBy());
		registrationStatusEntity.setUpdateDateTime(dto.getUpdateDateTime());
		registrationStatusEntity.setIsDeleted(dto.isDeleted());

		if (registrationStatusEntity.isDeleted() != null && registrationStatusEntity.isDeleted()) {
			registrationStatusEntity.setDeletedDateTime(LocalDateTime.now(ZoneId.of("UTC")));
		} else {
			registrationStatusEntity.setDeletedDateTime(null);
		}

		registrationStatusEntity.setRetryCount(dto.getRetryCount());
		registrationStatusEntity.setApplicantType(dto.getApplicantType());
		registrationStatusEntity.setRegProcessRetryCount(dto.getReProcessRetryCount());
		registrationStatusEntity.setLatestTransactionStatusCode(dto.getLatestTransactionStatusCode());
		registrationStatusEntity.setLatestTransactionTypeCode(dto.getLatestTransactionTypeCode());
		registrationStatusEntity.setRegistrationStageName(dto.getRegistrationStageName());
		registrationStatusEntity.setLatestTransactionTimes(dto.getLatestTransactionTimes());
		registrationStatusEntity.setResumeTimeStamp(dto.getResumeTimeStamp());
		registrationStatusEntity.setDefaultResumeAction(dto.getDefaultResumeAction());
		registrationStatusEntity.setNeedsNotification(dto.getNeedsNotification());
		registrationStatusEntity.setNotificationSent(dto.getNotificationSent());
		registrationStatusEntity.setIsAnonymousProfileAdded(dto.getIsAnonymousProfileAdded());
		return registrationStatusEntity;
	}
	
}
