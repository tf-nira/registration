package io.mosip.registration.processor.stages.introducervalidator;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.kernel.core.dataaccess.exception.DataAccessLayerException;
import io.mosip.registration.processor.core.code.*;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.packet.storage.entity.ManualVerificationEntity;
import io.mosip.registration.processor.packet.storage.entity.ManualVerificationPKEntity;
import io.mosip.registration.processor.packet.storage.exception.UnableToInsertData;
import io.mosip.registration.processor.packet.storage.repository.BasePacketRepository;
import io.mosip.registration.processor.packet.storage.utils.PriorityBasedPacketManagerService;
import io.mosip.registration.processor.status.exception.RegStatusAppException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.abstractverticle.MessageBusAddress;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.exception.AuthSystemException;
import io.mosip.registration.processor.core.exception.BiometricAuthenticationFailedException;
import io.mosip.registration.processor.core.exception.DataMigrationPacketCreationException;
import io.mosip.registration.processor.core.exception.IntroducerOnHoldException;
import io.mosip.registration.processor.core.exception.LegacyDataValidationException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.exception.ValidationFailedException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.exception.util.PlatformSuccessMessages;
import io.mosip.registration.processor.core.logger.LogDescription;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.status.util.StatusUtil;
import io.mosip.registration.processor.core.status.util.TrimExceptionMessage;
import io.mosip.registration.processor.core.util.RegistrationExceptionMapperUtil;
import io.mosip.registration.processor.packet.storage.exception.ParsingException;
import io.mosip.registration.processor.rest.client.audit.builder.AuditLogRequestBuilder;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.exception.TablenotAccessibleException;
import io.mosip.registration.processor.status.service.RegistrationStatusService;

@Service
@Transactional
public class IntroducerValidationProcessor {

	private static Logger regProcLogger = RegProcessorLogger.getLogger(IntroducerValidationProcessor.class);

	private TrimExceptionMessage trimExpMessage = new TrimExceptionMessage();

	private static final String USER = "MOSIP_SYSTEM";

	public static final String GLOBAL_CONFIG_TRUE_VALUE = "Y";

	@Autowired
	private RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

	@Autowired
	private AuditLogRequestBuilder auditLogRequestBuilder;

	@Autowired
	private RegistrationExceptionMapperUtil registrationStatusMapperUtil;

	@Autowired
	private IntroducerValidator introducerValidator;

	@Autowired
	private PriorityBasedPacketManagerService packetManagerService;

	@Autowired
	private BasePacketRepository<ManualVerificationEntity, String> manualVerficationRepository;

	public MessageDTO process(MessageDTO object, String stageName) {

		LogDescription description = new LogDescription();
		boolean isTransactionSuccessful = false;
		String registrationId = "";
		object.setMessageBusAddress(MessageBusAddress.INTRODUCER_VALIDATOR_BUS_IN);
		object.setIsValid(Boolean.FALSE);
		object.setInternalError(Boolean.TRUE);
		object.setOnHold(Boolean.FALSE);

		regProcLogger.debug("process called for registrationId {}", registrationId);
		registrationId = object.getRid();

		InternalRegistrationStatusDto registrationStatusDto = registrationStatusService
				.getRegistrationStatus(registrationId, object.getReg_type(), object.getIteration(), object.getWorkflowInstanceId());

		registrationStatusDto
				.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.INTRODUCER_VALIDATION.toString());
		registrationStatusDto.setRegistrationStageName(stageName);

		String nin = "";
		try {
			nin = packetManagerService.getField(registrationId, "introducerNIN", object.getReg_type(), ProviderStageName.INTRODUCER_VALIDATOR);
			introducerValidator.validate(registrationId, registrationStatusDto);

			registrationStatusDto.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.SUCCESS.toString());
			registrationStatusDto.setStatusComment(StatusUtil.INTRODUCER_VALIDATION_SUCCESS.getMessage());
			registrationStatusDto.setSubStatusCode(StatusUtil.INTRODUCER_VALIDATION_SUCCESS.getCode());
			registrationStatusDto.setStatusCode(RegistrationStatusCode.PROCESSING.toString());

			description.setMessage(
					PlatformSuccessMessages.RPR_PKR_INTRODUCER_VALIDATE.getMessage() + " -- " + registrationId);
			description.setCode(PlatformSuccessMessages.RPR_PKR_INTRODUCER_VALIDATE.getCode());

			regProcLogger.info("process call ended for registrationId {} {} {}", registrationId,
					description.getCode() + description.getMessage());

			object.setIsValid(Boolean.TRUE);
			object.setInternalError(Boolean.FALSE);
			isTransactionSuccessful = true;
		} catch (DataMigrationPacketCreationException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED,
					StatusUtil.DATA_MIGRATION_API_FAILED,
					RegistrationExceptionTypeCode.DATA_MIGRATION_PACKET_CREATION_EXCEPTION, description,
					PlatformErrorMessages.RPR_LEGACY_DATA_FAILED, e);
		} catch (LegacyDataValidationException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED,
					StatusUtil.LEGACY_DATA_SYSTEM_FAILED, RegistrationExceptionTypeCode.LEGACY_FAILED, description,
					PlatformErrorMessages.RPR_LEGACY_DATA_FAILED, e);
			Map<String, String> notificationAttributes = new HashMap<>();
			notificationAttributes.put("FAILURE_REASON", StatusUtil.INTRODUCER_NIN_ONDEMAND_MIGRATION_FAILED.getMessage());
			object.setNotificationAttributes(notificationAttributes);
		} catch (PacketManagerException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.PROCESSING,
					StatusUtil.PACKET_MANAGER_EXCEPTION, RegistrationExceptionTypeCode.PACKET_MANAGER_EXCEPTION,
					description, PlatformErrorMessages.PACKET_MANAGER_EXCEPTION, e);
		} catch (IntroducerOnHoldException e) {
			registrationStatusDto.setLatestTransactionStatusCode(
					RegistrationTransactionStatusCode.ON_HOLD.toString());
			registrationStatusDto.setStatusComment(e.getMessage());
			registrationStatusDto.setSubStatusCode(StatusUtil.PACKET_ON_HOLD.getCode());
			registrationStatusDto.setStatusCode(RegistrationStatusCode.ON_HOLD.toString());
			object.setOnHold(Boolean.TRUE);
		} catch (DataAccessException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.PROCESSING,
					StatusUtil.DB_NOT_ACCESSIBLE, RegistrationExceptionTypeCode.DATA_ACCESS_EXCEPTION, description,
					PlatformErrorMessages.RPR_RGS_REGISTRATION_TABLE_NOT_ACCESSIBLE, e);
		} catch (AuthSystemException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.PROCESSING,
					StatusUtil.AUTH_SYSTEM_EXCEPTION, RegistrationExceptionTypeCode.AUTH_SYSTEM_EXCEPTION, description,
					PlatformErrorMessages.RPR_AUTH_SYSTEM_EXCEPTION, e);
		} catch (IOException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED, StatusUtil.IO_EXCEPTION,
					RegistrationExceptionTypeCode.IOEXCEPTION, description, PlatformErrorMessages.RPR_SYS_IO_EXCEPTION,
					e);
		} catch (ParsingException | JsonProcessingException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED,
					StatusUtil.JSON_PARSING_EXCEPTION, RegistrationExceptionTypeCode.PARSE_EXCEPTION, description,
					PlatformErrorMessages.RPR_SYS_JSON_PARSING_EXCEPTION, e);
		} catch (TablenotAccessibleException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.PROCESSING,
					StatusUtil.DB_NOT_ACCESSIBLE, RegistrationExceptionTypeCode.TABLE_NOT_ACCESSIBLE_EXCEPTION,
					description, PlatformErrorMessages.RPR_RGS_REGISTRATION_TABLE_NOT_ACCESSIBLE, e);
		} catch (ValidationFailedException e) {
			// This also catches BiometricAuthenticationFailedException since it extends ValidationFailedException
			Map<String, String> notificationAttributes = new HashMap<>();
        	notificationAttributes.put("FAILURE_REASON", e.getErrorText());
        	object.setNotificationAttributes(notificationAttributes);

			try {
				saveManualAdjudicationData(object, nin);
				object.setMessageBusAddress(MessageBusAddress.MANUAL_ADJUDICATION_BUS_IN);
			} catch (RegStatusAppException ex) {
				regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
						"", e.getMessage() + io.mosip.kernel.core.exception.ExceptionUtils.getStackTrace(e));
			}

			object.setInternalError(Boolean.FALSE);
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED,
					StatusUtil.VALIDATION_FAILED_EXCEPTION, RegistrationExceptionTypeCode.VALIDATION_FAILED_EXCEPTION,
					description, PlatformErrorMessages.INTRODUCER_VALIDATION_FAILED, e);
		} catch (BaseUncheckedException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED,
					StatusUtil.BASE_UNCHECKED_EXCEPTION, RegistrationExceptionTypeCode.BASE_UNCHECKED_EXCEPTION,
					description, PlatformErrorMessages.INTRODUCER_BASE_UNCHECKED_EXCEPTION, e);
		} catch (BaseCheckedException e) {
			Map<String, String> notificationAttributes = new HashMap<>();

			try {
				List<String> errorTexts = e.getErrorTexts();
				if (errorTexts != null && !errorTexts.isEmpty()) {
					notificationAttributes.put("FAILURE_REASON", errorTexts.get(0));
					object.setNotificationAttributes(notificationAttributes);
				}
			} catch (NullPointerException ex) {
				// do nothing only set FAILURE_REASON if there are valid error texts
			}
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED,
					StatusUtil.BASE_CHECKED_EXCEPTION, RegistrationExceptionTypeCode.BASE_CHECKED_EXCEPTION,
					description, PlatformErrorMessages.INTRODUCER_BASE_CHECKED_EXCEPTION, e);
		} catch (Exception e) {
			// Check if the exception contains BiometricAuthenticationFailedException or MismatchedInputException in its cause chain
			if (BiometricAuthenticationFailedException.isBiometricAuthFailure(e)) {
				try {
					saveManualAdjudicationData(object, nin);
					object.setMessageBusAddress(MessageBusAddress.MANUAL_ADJUDICATION_BUS_IN);
				} catch (RegStatusAppException ex) {
					regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
							"", e.getMessage() + io.mosip.kernel.core.exception.ExceptionUtils.getStackTrace(e));
				}
				object.setInternalError(Boolean.FALSE);
				updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED,
						StatusUtil.VALIDATION_FAILED_EXCEPTION, RegistrationExceptionTypeCode.VALIDATION_FAILED_EXCEPTION,
						description, PlatformErrorMessages.INTRODUCER_VALIDATION_FAILED, e);
			} else {
				updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED,
						StatusUtil.UNKNOWN_EXCEPTION_OCCURED, RegistrationExceptionTypeCode.EXCEPTION, description,
						PlatformErrorMessages.INTRODUCER_VALIDATION_FAILED, e);
			}
		} finally {
			if (object.getInternalError()) {
				int retryCount = registrationStatusDto.getRetryCount() != null
						? registrationStatusDto.getRetryCount() + 1
						: 1;
				registrationStatusDto.setRetryCount(retryCount);
				updateErrorFlags(registrationStatusDto, object);
			}
			registrationStatusDto.setUpdatedBy(USER);
			/** Module-Id can be Both Success/Error code */
			String moduleId = description.getCode();
			String moduleName = ModuleName.INTRODUCER_VALIDATOR.toString();
			if (registrationStatusDto.getStatusCode() == RegistrationStatusCode.ON_HOLD.toString()) {
				registrationStatusService.updateRegistrationStatusForWorkflowEngine(registrationStatusDto, moduleId, moduleName);
			} else {
				registrationStatusService.updateRegistrationStatus(registrationStatusDto, moduleId, moduleName);
			}
			updateAudit(description, isTransactionSuccessful, moduleId, moduleName, registrationId);
		}

		return object;
	}

	private void updateDTOsAndLogError(InternalRegistrationStatusDto registrationStatusDto,
			RegistrationStatusCode registrationStatusCode, StatusUtil statusUtil,
			RegistrationExceptionTypeCode registrationExceptionTypeCode, LogDescription description,
			PlatformErrorMessages platformErrorMessages, Exception e) {
		registrationStatusDto.setStatusCode(registrationStatusCode.toString());
		registrationStatusDto
				.setStatusComment(trimExpMessage.trimExceptionMessage(statusUtil.getMessage() + e.getMessage()));
		registrationStatusDto.setSubStatusCode(statusUtil.getCode());
		registrationStatusDto.setLatestTransactionStatusCode(
				registrationStatusMapperUtil.getStatusCode(registrationExceptionTypeCode));
		description.setMessage(platformErrorMessages.getMessage());
		description.setCode(platformErrorMessages.getCode());
		regProcLogger.error("Error in  process  for registration id  {} {} {} {} {}",
				registrationStatusDto.getRegistrationId(), description.getCode(), platformErrorMessages.getMessage(),
				e.getMessage(), ExceptionUtils.getStackTrace(e));
	}

	private void updateAudit(LogDescription description, boolean isTransactionSuccessful, String moduleId,
			String moduleName, String registrationId) {
		String eventId = isTransactionSuccessful ? EventId.RPR_402.toString() : EventId.RPR_405.toString();
		String eventName = isTransactionSuccessful ? EventName.UPDATE.toString() : EventName.EXCEPTION.toString();
		String eventType = isTransactionSuccessful ? EventType.BUSINESS.toString() : EventType.SYSTEM.toString();

		auditLogRequestBuilder.createAuditRequestBuilder(description.getMessage(), eventId, eventName, eventType,
				moduleId, moduleName, registrationId);
	}
	
	private void updateErrorFlags(InternalRegistrationStatusDto registrationStatusDto, MessageDTO object) {
		object.setInternalError(true);
		if (registrationStatusDto.getLatestTransactionStatusCode()
				.equalsIgnoreCase(RegistrationTransactionStatusCode.REPROCESS.toString())) {
			object.setIsValid(true);
		} else {
			object.setIsValid(false);
		}
	}

	private void saveManualAdjudicationData(MessageDTO messageDTO, String nin) throws RegStatusAppException {
		boolean isTransactionSuccessful = false;
		LogDescription description = new LogDescription();
		String registrationId = messageDTO.getRid();

		try {
			regProcLogger.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(),
					registrationId, "IntroducerValidationStage::saveManualAdjudicationData()::entry");

			ManualVerificationEntity manualVerificationEntity = new ManualVerificationEntity();
			ManualVerificationPKEntity manualVerificationPKEntity = new ManualVerificationPKEntity();
			ObjectMapper mapper = new ObjectMapper();
			byte[] ninBytes = mapper.writeValueAsBytes(nin);
			String base64String = Base64.getEncoder().encodeToString(ninBytes);
			manualVerificationPKEntity.setMatchedRefId(base64String);
			manualVerificationPKEntity.setMatchedRefType("NIN");
			manualVerificationPKEntity.setWorkflowInstanceId(messageDTO.getWorkflowInstanceId());

			manualVerificationEntity.setRegId(registrationId);
			manualVerificationEntity.setId(manualVerificationPKEntity);
			manualVerificationEntity.setLangCode("eng");
			manualVerificationEntity.setRequestId(UUID.randomUUID().toString());
			manualVerificationEntity.setReponseText(null);
			manualVerificationEntity.setRequestId(null);
			manualVerificationEntity.setTransactionId(null);
			manualVerificationEntity.setMvUsrId(null);
			manualVerificationEntity.setReasonCode("Potential Match");
			manualVerificationEntity.setStatusCode("PENDING");
			manualVerificationEntity.setStatusComment("Assigned to manual Adjudication");
			manualVerificationEntity.setIsActive(true);
			manualVerificationEntity.setIsDeleted(false);
			manualVerificationEntity.setCrBy("SYSTEM");
			manualVerificationEntity.setCrDtimes(Timestamp.valueOf(LocalDateTime.now(ZoneId.of("UTC"))));
			manualVerificationEntity.setUpdDtimes(Timestamp.valueOf(LocalDateTime.now(ZoneId.of("UTC"))));
			manualVerificationEntity.setTrnTypCode(DedupeSourceName.INTRODUCER_VALIDATION_FAILURE.toString());
			manualVerficationRepository.save(manualVerificationEntity);
			isTransactionSuccessful = true;
			description.setMessage("Manual Adjudication data saved successfully");


		} catch (DataAccessLayerException e) {
			description.setMessage("DataAccessLayerException while saving Manual Adjudication data for rid"
					+ registrationId + "::" + e.getMessage());

			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					"", e.getMessage() + ExceptionUtils.getStackTrace(e));

			throw new UnableToInsertData(
					PlatformErrorMessages.RPR_PIS_UNABLE_TO_INSERT_DATA.getMessage() + registrationId, e);
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			throw new RuntimeException(e);
		} finally {

			String eventId = isTransactionSuccessful ? EventId.RPR_407.toString() : EventId.RPR_405.toString();
			String eventName = eventId.equalsIgnoreCase(EventId.RPR_407.toString()) ? EventName.ADD.toString()
					: EventName.EXCEPTION.toString();
			String eventType = eventId.equalsIgnoreCase(EventId.RPR_407.toString()) ? EventType.BUSINESS.toString()
					: EventType.SYSTEM.toString();

			auditLogRequestBuilder.createAuditRequestBuilder(description.getMessage(), eventId, eventName, eventType,
					PlatformErrorMessages.INTRODUCER_VALIDATION_FAILED.getCode(), ModuleName.INTRODUCER_VALIDATOR.toString(), registrationId);

		}
		regProcLogger.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(),
				registrationId, "IntroducerValidationStage::saveManualAdjudicationData()::exit");
	}
}
