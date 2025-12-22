package io.mosip.registration.processor.stages.legacy.data.val.stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.abstractverticle.MessageBusAddress;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.code.EventId;
import io.mosip.registration.processor.core.code.EventName;
import io.mosip.registration.processor.core.code.EventType;
import io.mosip.registration.processor.core.code.ModuleName;
import io.mosip.registration.processor.core.code.RegistrationExceptionTypeCode;
import io.mosip.registration.processor.core.code.RegistrationTransactionStatusCode;
import io.mosip.registration.processor.core.code.RegistrationTransactionTypeCode;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.DataMigrationPacketCreationException;
import io.mosip.registration.processor.core.exception.LegacyDataBiomtericException;
import io.mosip.registration.processor.core.exception.LegacyDataValidationException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.exception.ValidationFailedException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
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

@RefreshScope
@Service
@Transactional
public class LegacyDataProcessor {
	/**
	 * The reg proc logger.
	 */
	private static Logger regProcLogger = RegProcessorLogger.getLogger(LegacyDataProcessor.class);
	
	private TrimExceptionMessage trimExpMessage = new TrimExceptionMessage();

	/**
	 * The Constant USER.
	 */
	private static final String USER = "MOSIP_SYSTEM";

	/**
	 * The registration status service.
	 */
	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

	/**
	 * The core audit request builder.
	 */
	@Autowired
	AuditLogRequestBuilder auditLogRequestBuilder;

	@Autowired
	RegistrationExceptionMapperUtil registrationStatusMapperUtil;
	
	@Autowired
	private LegacyDataVal legacyDataVal;
	
	public MessageDTO process(MessageDTO object, String stageName) {
		LogDescription description = new LogDescription();
		boolean isTransactionSuccessful = false;
		String registrationId = "";
		object.setMessageBusAddress(MessageBusAddress.INTRODUCER_VALIDATOR_BUS_IN);
		object.setIsValid(Boolean.FALSE);
		object.setInternalError(Boolean.TRUE);
		object.setOnHold(false);
		Map<String, String> attributes = new HashMap<>();
		regProcLogger.debug("LegacyDataProcessor called for registrationId {}", registrationId);
		registrationId = object.getRid();

		InternalRegistrationStatusDto registrationStatusDto = registrationStatusService
				.getRegistrationStatus(registrationId, object.getReg_type(), object.getIteration(), object.getWorkflowInstanceId());

		registrationStatusDto
				.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.LEGACY_DATA.toString());
		registrationStatusDto.setRegistrationStageName(stageName);
		try {

			legacyDataVal.validate(registrationId, registrationStatusDto, description, object);
			regProcLogger.info("LegacyDataProcessor call ended for registrationId {} {} {}", registrationId,
					description.getCode() + description.getMessage());

			object.setIsValid(Boolean.TRUE);
			object.setInternalError(Boolean.FALSE);
			isTransactionSuccessful = true;
		} catch (DataMigrationPacketCreationException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.REJECTED,
					StatusUtil.LEGACY_DATA_MIGRATION_API_FAILED,
					RegistrationExceptionTypeCode.DATA_MIGRATION_PACKET_CREATION_EXCEPTION,
					description, PlatformErrorMessages.RPR_LEGACY_DATA_FAILED, e);
		} catch (LegacyDataValidationException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.LEGACYERROR,
					StatusUtil.LEGACY_DATA_SYSTEM_FAILED,
					RegistrationExceptionTypeCode.LEGACY_FAILED, description,
					PlatformErrorMessages.RPR_LEGACY_DATA_FAILED, e);
		} 
		catch (LegacyDataBiomtericException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED,
					StatusUtil.LEGACY_DATA_BIOMETRIC_FAILED,
					RegistrationExceptionTypeCode.LEGACY_FAILED, description,
					PlatformErrorMessages.RPR_LEGACY_DATA_FAILED, e);
			attributes.put("FAILURE_CODE", StatusUtil.LEGACY_DATA_BIOMETRIC_FAILED.getCode());
			attributes.put("FAILURE_REASON", StatusUtil.LEGACY_DATA_BIOMETRIC_FAILED.getMessage());
			object.setNotificationAttributes(attributes);
		} catch (PacketManagerException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.PROCESSING,
					StatusUtil.PACKET_MANAGER_EXCEPTION, RegistrationExceptionTypeCode.PACKET_MANAGER_EXCEPTION,
					description, PlatformErrorMessages.PACKET_MANAGER_EXCEPTION, e);
		} catch (DataAccessException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.PROCESSING,
					StatusUtil.DB_NOT_ACCESSIBLE, RegistrationExceptionTypeCode.DATA_ACCESS_EXCEPTION, description,
					PlatformErrorMessages.RPR_RGS_REGISTRATION_TABLE_NOT_ACCESSIBLE, e);
		} catch (ApisResourceAccessException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.PROCESSING,
					StatusUtil.API_RESOUCE_ACCESS_FAILED, RegistrationExceptionTypeCode.APIS_RESOURCE_ACCESS_EXCEPTION,
					description,
					PlatformErrorMessages.RPR_RGS_REGISTRATION_TABLE_NOT_ACCESSIBLE, e);
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
			object.setInternalError(Boolean.FALSE);
			object.setOnHold(true);
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.ON_HOLD,
					StatusUtil.LEGACY_DATA_FAILED, RegistrationExceptionTypeCode.ON_HOLD,
					description, PlatformErrorMessages.RPR_LEGACY_DATA_FAILED, e);
			// attributes.put("FAILURE_CODE", StatusUtil.LEGACY_DATA_FAILED.getCode());
			// attributes.put("FAILURE_REASON", StatusUtil.LEGACY_DATA_FAILED.getMessage());
			// object.setNotificationAttributes(attributes);
		} catch (BaseUncheckedException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED,
					StatusUtil.BASE_UNCHECKED_EXCEPTION, RegistrationExceptionTypeCode.BASE_UNCHECKED_EXCEPTION,
					description, PlatformErrorMessages.INTRODUCER_BASE_UNCHECKED_EXCEPTION, e);
		} catch (BaseCheckedException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED,
					StatusUtil.BASE_CHECKED_EXCEPTION, RegistrationExceptionTypeCode.BASE_CHECKED_EXCEPTION,
					description, PlatformErrorMessages.INTRODUCER_BASE_CHECKED_EXCEPTION, e);
		} catch (Exception e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED,
					StatusUtil.UNKNOWN_EXCEPTION_OCCURED, RegistrationExceptionTypeCode.EXCEPTION, description,
					PlatformErrorMessages.RPR_LEGACY_DATA_FAILED, e);
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
			String moduleName = ModuleName.LEGACY_DATA.toString();
			if (registrationStatusDto.getStatusCode().equals(RegistrationStatusCode.ON_HOLD.name())) {
				registrationStatusDto.setRegistrationStageName("BioDedupeStage");
				registrationStatusService.updateRegistrationStatusForWorkflowEngine(registrationStatusDto, moduleId,
						moduleName);
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
		description.setStatusComment(statusUtil.getMessage());
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

}
