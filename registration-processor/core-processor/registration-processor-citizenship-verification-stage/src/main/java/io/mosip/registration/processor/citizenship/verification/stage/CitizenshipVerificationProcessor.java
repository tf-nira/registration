package io.mosip.registration.processor.citizenship.verification.stage;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.citizenship.verification.constants.CitizenshipType;
import io.mosip.registration.processor.citizenship.verification.constants.Relationship;
import io.mosip.registration.processor.citizenship.verification.dto.ParentFoundDTO;
import io.mosip.registration.processor.citizenship.verification.service.NinUsageService;
import io.mosip.registration.processor.core.abstractverticle.MessageBusAddress;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.code.EventId;
import io.mosip.registration.processor.core.code.EventName;
import io.mosip.registration.processor.core.code.EventType;
import io.mosip.registration.processor.core.code.ModuleName;
import io.mosip.registration.processor.core.code.RegistrationExceptionTypeCode;
import io.mosip.registration.processor.core.code.RegistrationTransactionStatusCode;
import io.mosip.registration.processor.core.code.RegistrationTransactionTypeCode;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.DataMigrationPacketCreationException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.exception.PacketOnHoldException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.exception.util.PlatformSuccessMessages;
import io.mosip.registration.processor.core.logger.LogDescription;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.status.util.StatusUtil;
import io.mosip.registration.processor.core.status.util.TrimExceptionMessage;
import io.mosip.registration.processor.core.util.RegistrationExceptionMapperUtil;
import io.mosip.registration.processor.packet.storage.exception.IdRepoAppException;
import io.mosip.registration.processor.packet.storage.utils.MigrationUtil;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.rest.client.audit.builder.AuditLogRequestBuilder;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.service.RegistrationStatusService;

@Service
public class CitizenshipVerificationProcessor {

	private static final String USER = "MOSIP_SYSTEM";

	private TrimExceptionMessage trimExpMessage = new TrimExceptionMessage();

	private static Logger regProcLogger = RegProcessorLogger.getLogger(CitizenshipVerificationProcessor.class);

	@Autowired
	private AuditLogRequestBuilder auditLogRequestBuilder;

	@Autowired
	private NinUsageService ninUsageService;

	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

	@Autowired
	private Utilities utility;

	@Autowired
	RegistrationExceptionMapperUtil registrationStatusMapperUtil;

	private ObjectMapper objectMapper;

	@Autowired
	private MigrationUtil migrationUtil;

	@Value("${registration.processor.applicant.dob.format}")
	private String dobFormat;

	public MessageDTO process(MessageDTO object) {

		LogDescription description = new LogDescription();
		boolean isTransactionSuccessful = false;
		String registrationId = object.getRid();

		object.setMessageBusAddress(MessageBusAddress.CITIZENSHIP_VERIFICATION_BUS_IN);
		object.setIsValid(Boolean.FALSE);
		object.setInternalError(Boolean.FALSE);

		regProcLogger.debug("Process called for registrationId {}", registrationId);
		
		
		regProcLogger.debug("Registration Type: {}", object.getReg_type());
		regProcLogger.debug("Iteration: {}", object.getIteration());
		regProcLogger.debug("Workflow Instance ID: {}", object.getWorkflowInstanceId());

		InternalRegistrationStatusDto registrationStatusDto = registrationStatusService.getRegistrationStatus(
				registrationId, object.getReg_type(), object.getIteration(), object.getWorkflowInstanceId());
		
		if (registrationStatusDto == null) {
		    registrationStatusDto = new InternalRegistrationStatusDto();
		    regProcLogger.warn("Initialized registrationStatusDto with a default instance for registrationId {}", registrationId);
		}

		registrationStatusDto
				.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.CITIZENSHIP_VERIFICATION.toString());
		registrationStatusDto.setRegistrationStageName(ProviderStageName.CITIZENSHIP_VERIFICATION.toString());

		try {
			if (validatePacketCitizenship(registrationId, object, registrationStatusDto, description)) {
				object.setIsValid(Boolean.TRUE);
				object.setInternalError(Boolean.FALSE);
				regProcLogger.info("Citizenship Verification passed for registrationId: {}", registrationId);
				registrationStatusDto
						.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.SUCCESS.toString());
				registrationStatusDto.setStatusComment(StatusUtil.CITIZENSHIP_VERIFICATION_SUCCESS.getMessage());
				registrationStatusDto.setSubStatusCode(StatusUtil.CITIZENSHIP_VERIFICATION_SUCCESS.getCode());
				registrationStatusDto.setStatusCode(RegistrationStatusCode.PROCESSING.toString());

				description.setMessage(PlatformSuccessMessages.RPR_CITIZENSHIP_VERIFICATION_SUCCESS.getMessage()
						+ " -- " + registrationId);
				description.setCode(PlatformSuccessMessages.RPR_CITIZENSHIP_VERIFICATION_SUCCESS.getCode());
				isTransactionSuccessful = true;
			} else {

				object.setIsValid(Boolean.FALSE);
				object.setInternalError(Boolean.FALSE);
				regProcLogger.info(
						"Citizenship Verification failed for registrationId: {}. Packet goes to manual verification stage.",
						registrationId);
			}

		} catch (DataMigrationPacketCreationException e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED,
					StatusUtil.DATA_MIGRATION_API_FAILED,
					RegistrationExceptionTypeCode.DATA_MIGRATION_PACKET_CREATION_EXCEPTION, description,
					PlatformErrorMessages.RPR_CITIZENSHIP_VERIFICATION_FAILED, e);
		} catch (PacketManagerException e) {
			object.setInternalError(Boolean.TRUE);
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.PROCESSING,
					StatusUtil.PACKET_MANAGER_EXCEPTION, RegistrationExceptionTypeCode.PACKET_MANAGER_EXCEPTION,
					description, PlatformErrorMessages.PACKET_MANAGER_EXCEPTION, e);
		} catch (PacketOnHoldException e) {
			object.setInternalError(Boolean.TRUE);
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.PROCESSING, StatusUtil.PACKET_ON_HOLD,
					RegistrationExceptionTypeCode.ON_HOLD_CVS_PACKET, description,
					PlatformErrorMessages.RPR_CITIZENSHIP_VERIFICATION_FAILED, e);
		} catch (DataAccessException e) {
			object.setInternalError(Boolean.TRUE);
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.PROCESSING,
					StatusUtil.DB_NOT_ACCESSIBLE, RegistrationExceptionTypeCode.DATA_ACCESS_EXCEPTION, description,
					PlatformErrorMessages.RPR_RGS_REGISTRATION_TABLE_NOT_ACCESSIBLE, e);
		} catch (ApisResourceAccessException e) {
			object.setInternalError(Boolean.TRUE);
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.PROCESSING,
					StatusUtil.API_RESOUCE_ACCESS_FAILED, RegistrationExceptionTypeCode.APIS_RESOURCE_ACCESS_EXCEPTION,
					description, PlatformErrorMessages.RPR_SYS_API_RESOURCE_EXCEPTION, e);
		} catch (Exception e) {
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED,
					StatusUtil.UNKNOWN_EXCEPTION_OCCURED, RegistrationExceptionTypeCode.EXCEPTION, description,
					PlatformErrorMessages.RPR_CITIZENSHIP_VERIFICATION_FAILED, e);
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			regProcLogger.error("In Registration Processor", "Citizenship Verification",
					"Failed to validate citizenship for packet: " + e.getMessage());
		} finally {
			if (object.getInternalError()) {
				int retryCount = registrationStatusDto.getRetryCount() != null
						? registrationStatusDto.getRetryCount() + 1
						: 1;
				registrationStatusDto.setRetryCount(retryCount);
				updateErrorFlags(registrationStatusDto, object);
			}
			registrationStatusDto.setUpdatedBy(USER);
			String moduleId = description.getCode();
			String moduleName = ModuleName.CITIZENSHIP_VERIFICATION.toString();
			registrationStatusService.updateRegistrationStatus(registrationStatusDto, moduleId, moduleName);
			updateAudit(description, isTransactionSuccessful, moduleId, moduleName, registrationId);
		}

		return object;

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
		regProcLogger.error("Error in process for registration id {} {} {} {} {}",
				registrationStatusDto.getRegistrationId(), description.getCode(), platformErrorMessages.getMessage(),
				e.getMessage(), ExceptionUtils.getStackTrace(e));
	}

	private void logAndSetStatusError(InternalRegistrationStatusDto registrationStatusDto, String errorMessage,
			String subStatusCode, String statusComment, String statusCode, LogDescription description,
			String registrationId) {
		regProcLogger.error(errorMessage);
		registrationStatusDto.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.FAILED.toString());
		registrationStatusDto.setStatusComment(statusComment);
		registrationStatusDto.setSubStatusCode(subStatusCode);
		registrationStatusDto.setStatusCode(statusCode);

		description.setMessage(statusComment + " -- " + registrationId);
		description.setCode(subStatusCode);

		regProcLogger.info("Updated registrationStatusDto: {}", registrationStatusDto);
	}

	private boolean validatePacketCitizenship(String registrationId, MessageDTO object,
			InternalRegistrationStatusDto registrationStatusDto, LogDescription description)
			throws ApisResourceAccessException, PacketManagerException, JsonProcessingException, IOException,
			NoSuchAlgorithmException, DataMigrationPacketCreationException, PacketOnHoldException, JAXBException {
		boolean ifCitizenshipValid = false;

		objectMapper = new ObjectMapper();


			regProcLogger.info("Starting citizenship validation for registration ID: {}", registrationId);
		    regProcLogger.info("Registration type: {}", object.getReg_type());
			// Consolidate fields into a single list,
			List<String> fieldsToFetch = new ArrayList<>(List.of(MappingJsonConstants.APPLICANT_TRIBE,
					MappingJsonConstants.APPLICANT_CITIZENSHIPTYPE, MappingJsonConstants.APPLICANT_DATEOFBIRTH,
					MappingJsonConstants.APPLICANT_CLAN,MappingJsonConstants.FATHER_NIN, 
					MappingJsonConstants.MOTHER_NIN,MappingJsonConstants.GUARDIAN_NIN,
					MappingJsonConstants.GUARDIAN_RELATION_TO_APPLICANT
			));
			
			// Log the fields being fetched
	        regProcLogger.info("Fields to fetch for registration ID {}: {}", registrationId, fieldsToFetch);

			// Fetch all fields in a single call
			regProcLogger.info("Sending API request for registration ID: {}", registrationId);
			Map<String, String> applicantFields = utility.getPacketManagerService().getFields(registrationId,
					fieldsToFetch, object.getReg_type(), ProviderStageName.CITIZENSHIP_VERIFICATION);


			regProcLogger.info("fields fetched {}: " + applicantFields.toString());
			System.out.println("fields fetched {}: " + applicantFields.toString());
			String citizenshipType = null;
			String jsonCitizenshipTypes = applicantFields.get(MappingJsonConstants.APPLICANT_CITIZENSHIPTYPE);


				List<Map<String, String>> citizenshipTypes = objectMapper.readValue(jsonCitizenshipTypes,
						new TypeReference<List<Map<String, String>>>() {
						});
				citizenshipType = citizenshipTypes.get(0).get("value");

			System.out.println("****************************************************citizenshipType" + citizenshipType);
			if (!CitizenshipType.BIRTH.getCitizenshipType().equalsIgnoreCase(citizenshipType)) {
				regProcLogger.info("Citizenship verification failed: Not Citizen By Birth");
				
				logAndSetStatusError(registrationStatusDto,
						"Citizenship verification failed: Not Citizen By Birth for registrationId: " + registrationId,
						StatusUtil.CITIZENSHIP_VERIFICATION_NOT_CITIZEN_BYBIRTH.getCode(),
						StatusUtil.CITIZENSHIP_VERIFICATION_NOT_CITIZEN_BYBIRTH.getMessage(),
						RegistrationStatusCode.FAILED.toString(), description, registrationId);

				ifCitizenshipValid = false;

			} else {
				regProcLogger.info("Citizenship verification proceed: Citizen By Birth");

				applicantFields.put(MappingJsonConstants.AGE, String.valueOf(utility.getApplicantAge(registrationId,
						object.getReg_type(), ProviderStageName.CITIZENSHIP_VERIFICATION)));

				if (!checkIfAtLeastOneParentHasNIN(applicantFields)) {
					regProcLogger.info("Citizenship verification proceed: No parent has NIN");
					logAndSetStatusError(registrationStatusDto,
							"Citizenship verification proceed: No parent has NIN for registrationId: " + registrationId,
							StatusUtil.CITIZENSHIP_VERIFICATION_NO_PARENT_NIN.getCode(),
							StatusUtil.CITIZENSHIP_VERIFICATION_NO_PARENT_NIN.getMessage(),
							RegistrationStatusCode.FAILED.toString(), description, registrationId);
					ifCitizenshipValid = handleValidationWithNoParentNinFound(applicantFields, registrationStatusDto,
							description, object);
				} else {
					regProcLogger.info("Citizenship verification proceed: Atleast one parent has NIN");
					ifCitizenshipValid = handleValidationWithParentNinFound(applicantFields, registrationStatusDto,
							description);
				}
			}

		return ifCitizenshipValid;
	}

	private boolean checkIfAtLeastOneParentHasNIN(Map<String, String> fields) {
		String fatherNIN = fields.get("fatherNIN");
		String motherNIN = fields.get("motherNIN");
		return fatherNIN != null && !fatherNIN.isEmpty() || (motherNIN != null && !motherNIN.isEmpty());
	}

	private boolean handleValidationWithParentNinFound(Map<String, String> applicantFields,
			InternalRegistrationStatusDto registrationStatusDto, LogDescription description)
			throws JsonMappingException, com.fasterxml.jackson.core.JsonProcessingException,
			ApisResourceAccessException, NoSuchAlgorithmException, UnsupportedEncodingException,
			JsonProcessingException, DataMigrationPacketCreationException, JAXBException, PacketOnHoldException {

	    regProcLogger.info("Citizenship verification proceed: Handling validation with parents NIN found");
	    
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dobFormat);

	    String motherNIN = applicantFields.get(MappingJsonConstants.MOTHER_NIN);
	    String fatherNIN = applicantFields.get(MappingJsonConstants.FATHER_NIN);
	    regProcLogger.info("Father's NIN: " + fatherNIN);
        regProcLogger.info("Mother's NIN: " + motherNIN);

	    LocalDate applicantDob = parseDate(applicantFields.get(MappingJsonConstants.APPLICANT_DATEOFBIRTH), formatter);
	    regProcLogger.info("Parsed applicant date of birth from string '" + applicantDob + "' to LocalDate: " + applicantDob);

	    if (applicantDob == null) {
	        regProcLogger.error("Invalid applicant date of birth.");
	        return false;
	    }
        
	    boolean isParentInfoValid = false;
		ParentFoundDTO parentFoundDTO = new ParentFoundDTO();
		parentFoundDTO.setParentNINFoundInMosip(false);
	    if (fatherNIN != null) {
	        isParentInfoValid = validateParentInfo(fatherNIN, "FATHER", applicantFields, applicantDob, formatter,
					registrationStatusDto, description, parentFoundDTO);
	    }

	    if (isParentInfoValid == false && motherNIN != null) {
	        isParentInfoValid = validateParentInfo(motherNIN, "MOTHER", applicantFields, applicantDob, formatter,
					registrationStatusDto, description, parentFoundDTO);
	    }
		if (parentFoundDTO.isParentNINFoundInMosip() == false && isParentInfoValid == false) {
			boolean isOnDemandValid = validateOnDemandMigration(registrationStatusDto, motherNIN, fatherNIN);
			String fatherOrMother = "";
			if (fatherNIN != null) {
				fatherOrMother = "Father";
			} else {
				fatherOrMother = "Mother";
			}
			if (isOnDemandValid == true) {
				registrationStatusDto.setLatestTransactionStatusCode(
						registrationStatusMapperUtil.getStatusCode(RegistrationExceptionTypeCode.ON_HOLD_CVS_PACKET));
				registrationStatusDto.setStatusComment(
						StatusUtil.CITIZENSHIP_VERIFICATION_PACKET_ONHOLD.getMessage() + fatherOrMother
								+ StatusUtil.CITIZENSHIP_VERIFICATION_PACKET_INPROGRESS.getMessage());
				registrationStatusDto.setSubStatusCode(StatusUtil.CITIZENSHIP_VERIFICATION_PACKET_ONHOLD.getCode());
				registrationStatusDto.setStatusCode(RegistrationStatusCode.PROCESSING.toString());
				regProcLogger.debug("handleValidationWithParentNinFound call ended for registrationId {} {}",
						registrationStatusDto.getRegistrationId(),
						StatusUtil.CITIZENSHIP_VERIFICATION_PACKET_ONHOLD.getMessage());
				throw new PacketOnHoldException(StatusUtil.CITIZENSHIP_VERIFICATION_PACKET_ONHOLD.getCode(),
						StatusUtil.CITIZENSHIP_VERIFICATION_PACKET_ONHOLD.getMessage());
			} else {

				logAndSetStatusError(registrationStatusDto,
						StatusUtil.CITIZENSHIP_VERIFICATION_ONDEMAND_MIGRATION_FAILED.getMessage(),
						StatusUtil.CITIZENSHIP_VERIFICATION_ONDEMAND_MIGRATION_FAILED.getCode(),
						StatusUtil.CITIZENSHIP_VERIFICATION_ONDEMAND_MIGRATION_FAILED.getMessage() + fatherOrMother
								+ " failed",
						RegistrationStatusCode.FAILED.toString(), description, applicantFields.get("registrationId"));
				isParentInfoValid = false;
			}
		}

	 // Log error only if both NINs are missing or invalid
	    if (!isParentInfoValid && (fatherNIN == null && motherNIN == null)) {
	        regProcLogger.error("Neither parent's NIN is provided.");
	    }
	    return isParentInfoValid;
	}

	private boolean validateOnDemandMigration(InternalRegistrationStatusDto registrationStatusDto, String motherNIN,
			String fatherNIN) throws JAXBException, ApisResourceAccessException, NoSuchAlgorithmException,
			UnsupportedEncodingException, JsonProcessingException, JsonMappingException,
			com.fasterxml.jackson.core.JsonProcessingException, DataMigrationPacketCreationException {
		boolean isValid = false;
		if (fatherNIN != null) {
			regProcLogger.info("On demand migration of father NIN for rid {} {}", fatherNIN,
					registrationStatusDto.getRegistrationId());
			isValid = migrationUtil
					.validateAndCreateOnDemandPacket(registrationStatusDto.getRegistrationId(), fatherNIN);
		} else if (motherNIN != null) {
			regProcLogger.info("On demand migration of mother NIN for rid {} {}", fatherNIN,
					registrationStatusDto.getRegistrationId());
			isValid = migrationUtil.validateAndCreateOnDemandPacket(registrationStatusDto.getRegistrationId(),
					fatherNIN);
		}
		return isValid;
	}


	private boolean validateParentInfo(String parentNin, String parentType, Map<String, String> applicantFields,
			LocalDate applicantDob, DateTimeFormatter formatter, InternalRegistrationStatusDto registrationStatusDto,
			LogDescription description, ParentFoundDTO parentFoundDTO) {

		regProcLogger.info("Citizenship verification proceed: Validating parent");
		if (parentNin == null) {
			return false;
		}

		try {

			JSONObject parentInfoJson = utility.getIdentityJSONObjectByHandle(parentNin);
			if (parentInfoJson == null) {
				logAndSetStatusError(registrationStatusDto, parentType + "'s NIN not found in repo data.",
						StatusUtil.CITIZENSHIP_VERIFICATION_UIN_NOT_FOUND.getCode(),
						StatusUtil.CITIZENSHIP_VERIFICATION_UIN_NOT_FOUND.getMessage() + parentType + " information",
						RegistrationStatusCode.FAILED.toString(), description,
						applicantFields.get("registrationId"));
				return false;

			}
			parentFoundDTO.setParentNINFoundInMosip(true);
			if (ninUsageService.isNinUsedMorethanNtimes(parentNin, parentType)) {
				logAndSetStatusError(registrationStatusDto, parentType + "'s NIN is used more than N times.",
						StatusUtil.CITIZENSHIP_VERIFICATION_NIN_USAGE_EXCEEDED.getCode(),
						StatusUtil.CITIZENSHIP_VERIFICATION_NIN_USAGE_EXCEEDED.getMessage() + parentType
								+ " information",
						RegistrationStatusCode.FAILED.toString(), description,
						applicantFields.get("registrationId"));
				return false;
			}


			String parentDobStr = (String) parentInfoJson.get(MappingJsonConstants.APPLICANT_DATEOFBIRTH);
			LocalDate parentOrGuardianDob = parseDate(parentDobStr, formatter);
			regProcLogger.info("Parsed parent date of birth from string '" + parentDobStr + "' to LocalDate: "
					+ parentOrGuardianDob);

			if (parentOrGuardianDob == null
					|| !checkApplicantAgeWithParentOrGuardian(applicantDob, parentOrGuardianDob, 15)) {
				logAndSetStatusError(registrationStatusDto,
						parentType + "'s age difference with the applicant is less than 15 years.",
						StatusUtil.CITIZENSHIP_VERIFICATION_AGE_15_DIFFERENCE_FAILED.getCode(),
						StatusUtil.CITIZENSHIP_VERIFICATION_AGE_15_DIFFERENCE_FAILED.getMessage() + parentType
								+ " information",
						RegistrationStatusCode.PROCESSING.toString(), description,
						applicantFields.get("registrationId"));
				return false;

			}

			Map<String, String> person1Map = extractDemographics(parentType, parentInfoJson);
			regProcLogger.info("Extracted demographics for {}: {}", parentType, person1Map);

			Map<String, String> person2Map = extractApplicantDemographics(applicantFields);
			regProcLogger.info("Applicant Extracted demographics for {}: {}", parentType, person2Map);

			return ValidateTribeAndClan(person1Map, person2Map, registrationStatusDto, description, applicantFields,
					parentType);
		} catch (Exception e) {
			logAndSetStatusError(registrationStatusDto,
					"Error processing " + parentType + "'s information: " + e.getMessage(),
					StatusUtil.CITIZENSHIP_VERIFICATION_PARENT_INFO_PROCESSING_ERROR.getCode(),
					StatusUtil.CITIZENSHIP_VERIFICATION_PARENT_INFO_PROCESSING_ERROR.getMessage(),
					RegistrationStatusCode.FAILED.toString(), description, applicantFields.get("registrationId"));
			return false;
		}
	}

	private LocalDate parseDate(String dateStr, DateTimeFormatter formatter) {
		try {
			return LocalDate.parse(dateStr, formatter);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private Map<String, String> extractDemographics(String parentType, JSONObject parentInfoJson) {
		Map<String, String> person1Map = new HashMap<>();
		person1Map.put(MappingJsonConstants.PERSON, parentType + " in NIRA System");
		ObjectMapper objectMapper = new ObjectMapper();

		extractAndPutValue(person1Map, MappingJsonConstants.TRIBE, parentInfoJson, MappingJsonConstants.PARENT_TRIBE,
				objectMapper);
		extractAndPutValue(person1Map, MappingJsonConstants.CLAN, parentInfoJson, MappingJsonConstants.PARENT_CLAN,
				objectMapper);

		return person1Map;
	}

	private void extractAndPutValue(Map<String, String> map, String key, JSONObject jsonObject, String jsonKey,
			ObjectMapper objectMapper) {
		String jsonString = null;
		try {
			jsonString = jsonObject.get(jsonKey).toString();
		} catch (Exception e) {

		}
		if (jsonString != null && !jsonString.isEmpty()) {
			try {
				List<Map<String, String>> list = objectMapper.readValue(jsonString,
						new TypeReference<List<Map<String, String>>>() {
						});
				if (!list.isEmpty()) {
					map.put(key, list.get(0).get("value"));
				}
			} catch (Exception e) {

			}
		}
	}

	private Map<String, String> extractApplicantDemographics(Map<String, String> applicantFields) {
		Map<String, String> person2Map = new HashMap<>();
		person2Map.put(MappingJsonConstants.PERSON, "Applicant");
		ObjectMapper objectMapper = new ObjectMapper();

		extractAndPutValue(person2Map, MappingJsonConstants.TRIBE,applicantFields.get(MappingJsonConstants.APPLICANT_TRIBE), objectMapper);
		extractAndPutValue(person2Map, MappingJsonConstants.CLAN,applicantFields.get(MappingJsonConstants.APPLICANT_CLAN), objectMapper);

		return person2Map;
	}

	private void extractAndPutValue(Map<String, String> map, String key, String jsonString, ObjectMapper objectMapper) {
		if (jsonString != null && !jsonString.isEmpty()) {
			try {
				List<Map<String, String>> list = objectMapper.readValue(jsonString,
						new TypeReference<List<Map<String, String>>>() {
						});
				if (!list.isEmpty()) {
					map.put(key, list.get(0).get("value"));
				}
			} catch (Exception e) {

			}
		}
	}

	private boolean ValidateTribeAndClan(Map<String, String> person1, Map<String, String> person2,
			InternalRegistrationStatusDto registrationStatusDto, LogDescription description,
			Map<String, String> applicantFields, String parentType) {
		Boolean isValid = false;

		if (person1.get(MappingJsonConstants.TRIBE).equalsIgnoreCase(person2.get(MappingJsonConstants.TRIBE))) {

			if (person1.get(MappingJsonConstants.CLAN).equalsIgnoreCase(person2.get(MappingJsonConstants.CLAN))) {
				isValid = true;
			} else {

				logAndSetStatusError(registrationStatusDto,
						"Mismatch in " + person1.get(MappingJsonConstants.PERSON) + ", "
								+ person2.get(MappingJsonConstants.PERSON) + "'s " + MappingJsonConstants.CLAN
								+ " information.",
					StatusUtil.CITIZENSHIP_VERIFICATION_CLAN_MISMATCH.getCode(),
						StatusUtil.CITIZENSHIP_VERIFICATION_CLAN_MISMATCH.getMessage() + parentType + " information",
						RegistrationStatusCode.FAILED.toString(), description,
					applicantFields.get("registrationId"));
			}
		} else {
			logAndSetStatusError(registrationStatusDto, "Mismatch in " + person1.get(MappingJsonConstants.PERSON) + ", "
					+ person2.get(MappingJsonConstants.PERSON) + "'s " + MappingJsonConstants.TRIBE + " information.",
				StatusUtil.CITIZENSHIP_VERIFICATION_TRIBE_MISMATCH.getCode(),
					StatusUtil.CITIZENSHIP_VERIFICATION_TRIBE_MISMATCH.getMessage() + parentType + " information",
					RegistrationStatusCode.FAILED.toString(), description, applicantFields.get("registrationId"));
		}

		return isValid;
	}

	
	
	private boolean handleValidationWithNoParentNinFound(Map<String, String> applicantFields,
			InternalRegistrationStatusDto registrationStatusDto, LogDescription description,MessageDTO object)
			throws JsonMappingException, com.fasterxml.jackson.core.JsonProcessingException, NoSuchAlgorithmException,
			IdRepoAppException, ApisResourceAccessException, UnsupportedEncodingException, JsonProcessingException,
			DataMigrationPacketCreationException, JAXBException, PacketOnHoldException {

		String guardianNin = applicantFields.get(MappingJsonConstants.GUARDIAN_NIN);
		if (guardianNin == null) {

			logAndSetStatusError(registrationStatusDto, "GUARDIAN_NIN is missing. Stopping further processing.",
					StatusUtil.CITIZENSHIP_VERIFICATION_GUARDIAN_NIN_MISSING.getCode(),
					StatusUtil.CITIZENSHIP_VERIFICATION_GUARDIAN_NIN_MISSING.getMessage(),
					RegistrationStatusCode.FAILED.toString(), description, applicantFields.get("registrationId"));
			return false;
		} else {
			regProcLogger.info("GUARDIAN_NIN: " + guardianNin);
		}

		String guardianRelationToApplicantJson = applicantFields.get(MappingJsonConstants.GUARDIAN_RELATION_TO_APPLICANT);
		regProcLogger.info("GUARDIAN_RELATION_TO_APPLICANT: " + guardianRelationToApplicantJson);

		ObjectMapper objectMapper = new ObjectMapper();
		String guardianRelationValue = null;

			List<Map<String, String>> guardianRelations = objectMapper.readValue(guardianRelationToApplicantJson,
					new TypeReference<List<Map<String, String>>>() {
					});
			guardianRelationValue = guardianRelations.get(0).get("value");
			regProcLogger.info("GUARDIAN_RELATION_TO_APPLICANT: " + guardianRelationValue);


		boolean isValidGuardian = false;
		JSONObject guardianInfoJson = utility.getIdentityJSONObjectByHandle(guardianNin);
		regProcLogger.info("guardianInfoJson: " + guardianInfoJson);
		if(guardianInfoJson!=null) {

			if (ninUsageService.isNinUsedMorethanNtimes(guardianNin, guardianRelationValue)) {

				logAndSetStatusError(registrationStatusDto,
						"NIN usage is over the limit for guardian NIN: " + guardianNin + ", relation: "
								+ guardianRelationValue,
						StatusUtil.CITIZENSHIP_VERIFICATION_NIN_USAGE_EXCEEDED.getCode(),
						StatusUtil.CITIZENSHIP_VERIFICATION_NIN_USAGE_EXCEEDED.getMessage() + guardianRelationValue
								+ " information",
						RegistrationStatusCode.FAILED.toString(), description,
						applicantFields.get("registrationId"));
				return false;
			}
			
			if (guardianRelationValue.equalsIgnoreCase(Relationship.FIRST_COUSIN_FATHERS_SIDE.getRelationship()) 
				    || guardianRelationValue.equalsIgnoreCase(Relationship.FIRST_COUSIN_MOTHERS_SIDE.getRelationship())) {
				 regProcLogger.info("Validating first cousin relationship: " + guardianRelationValue);
				 object.setMessageBusAddress(MessageBusAddress.MVS_BUS_IN);
					return true; // Skip further validation for first cousins, only check NIN usage
				}

			// Validation for Uncle/Aunt Relationships
	        if (guardianRelationValue.equalsIgnoreCase(Relationship.MATERNAL_AUNT.getRelationship())
	                || guardianRelationValue.equalsIgnoreCase(Relationship.PATERNAL_AUNT.getRelationship())
	                || guardianRelationValue.equalsIgnoreCase(Relationship.MATERNAL_UNCLE.getRelationship())
	                || guardianRelationValue.equalsIgnoreCase(Relationship.PATERNAL_UNCLE.getRelationship())) {
	            regProcLogger.info("Skipping detailed validation for uncle/aunt relationship.");
	            object.setMessageBusAddress(MessageBusAddress.MVS_BUS_IN);
	            return true;
	        }


			if (guardianRelationValue.equalsIgnoreCase(Relationship.GRAND_FATHER_ON_FATHERS_SIDE.getRelationship())
					|| Relationship.GRAND_FATHER_ON_MOTHERS_SIDE.getRelationship()
							.equalsIgnoreCase(guardianRelationValue)) {
				isValidGuardian = validateGrandfatherRelationship(applicantFields, guardianInfoJson,
						registrationStatusDto, description);
				
			  } else if (guardianRelationValue.equalsIgnoreCase(Relationship.GRAND_MOTHER_ON_FATHERS_SIDE.getRelationship())
		                || guardianRelationValue.equalsIgnoreCase(Relationship.GRAND_MOTHER_ON_MOTHERS_SIDE.getRelationship())) {
		            isValidGuardian = validateGrandmotherRelationship(applicantFields, guardianInfoJson,
		                    registrationStatusDto, description);
					object.setMessageBusAddress(MessageBusAddress.MVS_BUS_IN);

			} else if (guardianRelationValue.equalsIgnoreCase(Relationship.BROTHER.getRelationship())
				    || guardianRelationValue.equalsIgnoreCase(Relationship.SISTER.getRelationship())) {
				isValidGuardian = validateSiblingRelationship(applicantFields, guardianInfoJson, registrationStatusDto,
						description);
				}

		}
			else {
				regProcLogger.info("On demand migration of guardian NIN for rid {} {}", guardianNin,
						registrationStatusDto.getRegistrationId());
				boolean isValid = migrationUtil
						.validateAndCreateOnDemandPacket(registrationStatusDto.getRegistrationId(),
						guardianNin);
				if (isValid) {
					registrationStatusDto.setLatestTransactionStatusCode(registrationStatusMapperUtil
							.getStatusCode(RegistrationExceptionTypeCode.ON_HOLD_CVS_PACKET));
					registrationStatusDto
							.setStatusComment(StatusUtil.CITIZENSHIP_VERIFICATION_PACKET_ONHOLD.getMessage()
									+ guardianRelationValue
									+ StatusUtil.CITIZENSHIP_VERIFICATION_PACKET_INPROGRESS.getMessage());
					registrationStatusDto.setSubStatusCode(StatusUtil.CITIZENSHIP_VERIFICATION_PACKET_ONHOLD.getCode());
					registrationStatusDto.setStatusCode(RegistrationStatusCode.PROCESSING.toString());
					regProcLogger.debug("handleValidationWithParentNinFound call ended for registrationId {} {}",
							registrationStatusDto.getRegistrationId(),
							StatusUtil.CITIZENSHIP_VERIFICATION_PACKET_ONHOLD.getMessage());
					throw new PacketOnHoldException(StatusUtil.CITIZENSHIP_VERIFICATION_PACKET_ONHOLD.getCode(),
							StatusUtil.CITIZENSHIP_VERIFICATION_PACKET_ONHOLD.getMessage());
				} else {
					logAndSetStatusError(registrationStatusDto,
							StatusUtil.CITIZENSHIP_VERIFICATION_ONDEMAND_MIGRATION_FAILED.getMessage(),
							StatusUtil.CITIZENSHIP_VERIFICATION_ONDEMAND_MIGRATION_FAILED.getCode(),
							StatusUtil.CITIZENSHIP_VERIFICATION_ONDEMAND_MIGRATION_FAILED.getMessage()
									+ guardianRelationValue + " failed",
							RegistrationStatusCode.FAILED.toString(), description,
							applicantFields.get("registrationId"));
				}
			}
			return isValidGuardian;

	}
	

	private boolean checkApplicantAgeWithParentOrGuardian(LocalDate applicantDob, LocalDate parentOrGuardianDob,
			int ageCondition) {
		Period ageDifference = Period.between(parentOrGuardianDob, applicantDob);
		regProcLogger.info("Age difference is: {} years, {} months, and {} days.", ageDifference.getYears(),
				ageDifference.getMonths(), ageDifference.getDays());
		return ageDifference.getYears() >= ageCondition;
	}


	private boolean validateGrandmotherRelationship(Map<String, String> applicantFields, JSONObject guardianInfoJson,
	        InternalRegistrationStatusDto registrationStatusDto, LogDescription description)
	        throws IdRepoAppException, ApisResourceAccessException {

	    // Retrieve the guardian's NIN and validate its presence
	    String guardianNin = applicantFields.get(MappingJsonConstants.GUARDIAN_NIN);
	    if (guardianNin == null) {
	        regProcLogger.warn("GUARDIAN_NIN is missing. Stopping further processing.");
	        return false;
	    } else {
	        regProcLogger.info("GUARDIAN_NIN: " + guardianNin);
	    }

	    // Retrieve and parse the relationship to the applicant
	    String guardianRelationToApplicantJson = applicantFields.get(MappingJsonConstants.GUARDIAN_RELATION_TO_APPLICANT);
	    regProcLogger.info("GUARDIAN_RELATION_TO_APPLICANT: " + guardianRelationToApplicantJson);

	    ObjectMapper objectMapper = new ObjectMapper();
	    String guardianRelationValue = null;
	    try {
	        List<Map<String, String>> guardianRelations = objectMapper.readValue(guardianRelationToApplicantJson,
	                new TypeReference<List<Map<String, String>>>() {});
	        guardianRelationValue = guardianRelations.get(0).get("value");
	        regProcLogger.info("GUARDIAN_RELATION_TO_APPLICANT: " + guardianRelationValue);
	    } catch (Exception e) {
	        regProcLogger.error("Error parsing GUARDIAN_RELATION_TO_APPLICANT JSON", e);
	        return false;
	    }

	    // Retrieve and parse dates of birth for the guardian and applicant
	    String guardianDobStr = (String) guardianInfoJson.get(MappingJsonConstants.APPLICANT_DATEOFBIRTH);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dobFormat);

	    LocalDate guardianDob = null;
	    LocalDate applicantDob = null;
	    try {
	        guardianDob = LocalDate.parse(guardianDobStr, formatter);
	        applicantDob = LocalDate.parse(applicantFields.get(MappingJsonConstants.APPLICANT_DATEOFBIRTH), formatter);
	    } catch (Exception e) {
	        regProcLogger.error("Error parsing dates of birth for guardian or applicant.", e);
	        return false;
	    }

	    regProcLogger.info("Applicant DOB: " + applicantDob);
	    regProcLogger.info("Guardian (grandmother) DOB: " + guardianDob);

	    // Validate age difference
	    if (!checkApplicantAgeWithParentOrGuardian(applicantDob, guardianDob, 20)) {
	        logAndSetStatusError(registrationStatusDto,
	                "Guardian (grandmother) is not at least 20 years older than the applicant for registrationId: "
	                        + applicantFields.get("registrationId"),
	                StatusUtil.CITIZENSHIP_VERIFICATION_AGE_DIFFERENCE_FAILED.getCode(),
					StatusUtil.CITIZENSHIP_VERIFICATION_AGE_DIFFERENCE_FAILED.getMessage() + guardianRelationValue
							+ " information",
					RegistrationStatusCode.FAILED.toString(), description, applicantFields.get("registrationId"));
	        return false;
	    }

	    return true;
	}


	private boolean validateGrandfatherRelationship(Map<String, String> applicantFields, JSONObject guardianInfoJson,
			InternalRegistrationStatusDto registrationStatusDto, LogDescription description)
			throws IdRepoAppException, ApisResourceAccessException {

		String guardianNin = applicantFields.get(MappingJsonConstants.GUARDIAN_NIN);
		if (guardianNin == null) {
			regProcLogger.warn("GUARDIAN_NIN is missing. Stopping further processing.");
			return false;
		} else {
			regProcLogger.info("GUARDIAN_NIN: " + guardianNin);
		}


		String guardianRelationToApplicantJson = applicantFields
				.get(MappingJsonConstants.GUARDIAN_RELATION_TO_APPLICANT);
		regProcLogger.info("GUARDIAN_RELATION_TO_APPLICANT: " + guardianRelationToApplicantJson);

		ObjectMapper objectMapper = new ObjectMapper();

		String guardianRelationValue = null;
		try {
			List<Map<String, String>> guardianRelations = objectMapper.readValue(guardianRelationToApplicantJson,
					new TypeReference<List<Map<String, String>>>() {
					});
			guardianRelationValue = guardianRelations.get(0).get("value");
			regProcLogger.info("GUARDIAN_RELATION_TO_APPLICANT: " + guardianRelationValue);
		} catch (Exception e) {
			regProcLogger.error("Error parsing GUARDIAN_RELATION_TO_APPLICANT JSON", e);
			return false;
		}

		boolean isValidGuardian = true;

		String guardianDobStr = (String) guardianInfoJson.get(MappingJsonConstants.APPLICANT_DATEOFBIRTH);
																										
																											
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dobFormat);
		LocalDate parentOrGuardianDob = LocalDate.parse(guardianDobStr, formatter);
		LocalDate applicantDob = LocalDate.parse(applicantFields.get(MappingJsonConstants.APPLICANT_DATEOFBIRTH),
				formatter);

		regProcLogger.info("Applicant DOB: " + applicantDob);
		regProcLogger.info("Guardian DOB: " + parentOrGuardianDob);

		if (!checkApplicantAgeWithParentOrGuardian(applicantDob, parentOrGuardianDob, 20)) {

			logAndSetStatusError(registrationStatusDto,
					"Guardian (grandfather) is not at least 20 years older than the applicant for registrationId: "
							+ applicantFields.get("registrationId"),
					StatusUtil.CITIZENSHIP_VERIFICATION_AGE_DIFFERENCE_FAILED.getCode(),
					StatusUtil.CITIZENSHIP_VERIFICATION_AGE_DIFFERENCE_FAILED.getMessage() + guardianRelationValue
							+ " information",
					RegistrationStatusCode.FAILED.toString(), description, applicantFields.get("registrationId"));
			isValidGuardian = false;
		}

		Map<String, String> guardian1Map = extractguardianDemographics(guardianRelationValue, guardianInfoJson);
		regProcLogger.info("Extracted demographics for {}: {}", guardianRelationValue, guardian1Map);

		Map<String, String> guardian2Map = extractguardianApplicantDemographics(applicantFields);
		regProcLogger.info("Extracted demographics for applicant: {}", guardian2Map);

		boolean isValidTribeAndClan = ValidateguardianTribeAndClan(guardian1Map, guardian2Map, registrationStatusDto,
				description, applicantFields, guardianRelationValue);

		
		return isValidGuardian && isValidTribeAndClan;
	}

	private Map<String, String> extractguardianDemographics(String guardianRelationValue, JSONObject guardianInfoJson) {
		Map<String, String> guardian1Map = new HashMap<>();
		guardian1Map.put(MappingJsonConstants.PERSON, guardianRelationValue + " in NIRA System");
		ObjectMapper objectMapper = new ObjectMapper();

		extractAndPutValuee(guardian1Map, MappingJsonConstants.TRIBE, guardianInfoJson,
				MappingJsonConstants.GUARDIAN_TRIBE, objectMapper);
		extractAndPutValuee(guardian1Map, MappingJsonConstants.CLAN, guardianInfoJson,
				MappingJsonConstants.GUARDIAN_CLAN, objectMapper);

		return guardian1Map;
	}

	private void extractAndPutValuee(Map<String, String> map, String key, JSONObject jsonObject, String jsonKey,
			ObjectMapper objectMapper) {
		String jsonString = null;
		try {
			jsonString = jsonObject.get(jsonKey).toString();
		} catch (Exception e) {

		}
		if (jsonString != null && !jsonString.isEmpty()) {
			try {
				List<Map<String, String>> list = objectMapper.readValue(jsonString,
						new TypeReference<List<Map<String, String>>>() {
						});
				if (!list.isEmpty()) {
					map.put(key, list.get(0).get("value"));
				}
			} catch (Exception e) {

			}
		}
	}

	private Map<String, String> extractguardianApplicantDemographics(Map<String, String> applicantFields) {
		Map<String, String> guardian2Map = new HashMap<>();
		guardian2Map.put(MappingJsonConstants.PERSON, "Applicant");
		ObjectMapper objectMapper = new ObjectMapper();

		extractAndPutValueee(guardian2Map, MappingJsonConstants.TRIBE,applicantFields.get(MappingJsonConstants.APPLICANT_TRIBE), objectMapper);
		extractAndPutValueee(guardian2Map, MappingJsonConstants.CLAN,applicantFields.get(MappingJsonConstants.APPLICANT_CLAN), objectMapper);

		return guardian2Map;
	}

	private void extractAndPutValueee(Map<String, String> map, String key, String jsonString,
			ObjectMapper objectMapper) {
		if (jsonString == null || jsonString.isEmpty()) {
			regProcLogger.error("JSON string is null or empty for key: " + key);
			return;
		}

		try {
			List<Map<String, String>> list = objectMapper.readValue(jsonString,
					new TypeReference<List<Map<String, String>>>() {
					});
			if (list.isEmpty()) {
				regProcLogger.error("JSON list is empty for key: " + key);
				return;
			}
			String value = list.get(0).get("value");
			if (value == null) {
				regProcLogger.error("Value is missing in the JSON list for key: " + key);
				return;
			}
			map.put(key, value);
		} catch (Exception e) {
			regProcLogger.error("Error parsing JSON string for key: " + key, e);
		}
	}

	private boolean validateSiblingRelationship(Map<String, String> applicantFields, JSONObject guardianInfoJson,
			InternalRegistrationStatusDto registrationStatusDto, LogDescription description)
			throws IdRepoAppException, ApisResourceAccessException, JsonMappingException,
			com.fasterxml.jackson.core.JsonProcessingException {

		String guardianNin = applicantFields.get(MappingJsonConstants.GUARDIAN_NIN);
		if (guardianNin == null) {
			regProcLogger.warn("GUARDIAN_NIN is missing. Stopping further processing.");
			return false;
		} else {
			regProcLogger.info("GUARDIAN_NIN: " + guardianNin);
		}


		String guardianRelationToApplicantJson = applicantFields
				.get(MappingJsonConstants.GUARDIAN_RELATION_TO_APPLICANT);
		regProcLogger.info("GUARDIAN_RELATION_TO_APPLICANT: " + guardianRelationToApplicantJson);

		ObjectMapper objectMapper = new ObjectMapper();

		String guardianRelationValue = null;

			List<Map<String, String>> guardianRelations = objectMapper.readValue(guardianRelationToApplicantJson,
					new TypeReference<List<Map<String, String>>>() {
					});
			guardianRelationValue = guardianRelations.get(0).get("value");
			regProcLogger.info("GUARDIAN_RELATION_TO_APPLICANT: " + guardianRelationValue);



		Map<String, String> guardian1Map = extractguardianDemographics(guardianRelationValue, guardianInfoJson);
		regProcLogger.info("Extracted demographics for {}: {}", guardianRelationValue, guardian1Map);

		Map<String, String> guardian2Map = extractguardianApplicantDemographics(applicantFields);
		regProcLogger.info("Extracted demographics for applicant: {}", guardian2Map);

		boolean isValidTribeAndClan = ValidateguardianTribeAndClan(guardian1Map, guardian2Map, registrationStatusDto,
				description,
				applicantFields, guardianRelationValue);

		return isValidTribeAndClan;
	}


	private boolean ValidateguardianTribeAndClan(Map<String, String> guardian1, Map<String, String> guardian2,
			InternalRegistrationStatusDto registrationStatusDto, LogDescription description,
			Map<String, String> applicantFields, String guardianRelationValue) {
		Boolean isValid = false;
		if (guardian1.get(MappingJsonConstants.TRIBE).equalsIgnoreCase(guardian2.get(MappingJsonConstants.TRIBE))) {

			if (guardian1.get(MappingJsonConstants.CLAN).equalsIgnoreCase(guardian2.get(MappingJsonConstants.CLAN))) {

				{
					isValid = true;

				}
			} else {

				logAndSetStatusError(registrationStatusDto,
						"Mismatch in " + guardian1.get(MappingJsonConstants.PERSON) + ", "
								+ guardian2.get(MappingJsonConstants.PERSON) + "'s " + MappingJsonConstants.CLAN
								+ " information.",
						StatusUtil.CITIZENSHIP_VERIFICATION_CLAN_MISMATCH.getCode(),
						StatusUtil.CITIZENSHIP_VERIFICATION_CLAN_MISMATCH.getMessage() + guardianRelationValue
								+ " information",
						RegistrationStatusCode.FAILED.toString(), description,
						applicantFields.get("registrationId"));
			}
		} else {

			logAndSetStatusError(registrationStatusDto,
					"Mismatch in " + guardian1.get(MappingJsonConstants.PERSON) + ", "
							+ guardian2.get(MappingJsonConstants.PERSON) + "'s " + MappingJsonConstants.TRIBE
							+ " information.",
					StatusUtil.CITIZENSHIP_VERIFICATION_TRIBE_MISMATCH.getCode(),
					StatusUtil.CITIZENSHIP_VERIFICATION_TRIBE_MISMATCH.getMessage() + guardianRelationValue
							+ " information",
					RegistrationStatusCode.FAILED.toString(), description, applicantFields.get("registrationId"));
		}

		return isValid;
	}

}
