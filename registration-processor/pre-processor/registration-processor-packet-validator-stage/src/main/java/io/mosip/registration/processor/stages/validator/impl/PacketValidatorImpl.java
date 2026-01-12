package io.mosip.registration.processor.stages.validator.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.core.cbeffutil.exception.CbeffException;
import io.mosip.kernel.core.exception.BiometricSignatureValidationException;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.StringUtils;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.code.RegistrationTransactionStatusCode;
import io.mosip.registration.processor.core.constant.JsonConstant;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.constant.RegistrationType;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.exception.RegistrationProcessorCheckedException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.idrepo.dto.CardDetailDto;
import io.mosip.registration.processor.core.idrepo.dto.ResponseDTO;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.packet.dto.FieldValue;
import io.mosip.registration.processor.core.packet.dto.packetvalidator.PacketValidationDto;
import io.mosip.registration.processor.core.spi.packet.validator.PacketValidator;
import io.mosip.registration.processor.core.status.util.StatusUtil;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.packet.storage.dto.ValidatePacketResponse;
import io.mosip.registration.processor.packet.storage.exception.IdRepoAppException;
import io.mosip.registration.processor.packet.storage.utils.PriorityBasedPacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.stages.utils.ApplicantDocumentValidation;
import io.mosip.registration.processor.stages.utils.BiometricsXSDValidator;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.entity.RegistrationStatusEntity;
import io.mosip.registration.processor.status.repositary.RegistrationRepositary;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Component
@RefreshScope
public class PacketValidatorImpl implements PacketValidator {

	private static Logger regProcLogger = RegProcessorLogger.getLogger(PacketValidatorImpl.class);
	private static final String VALIDATIONFALSE = "false";
	public static final String APPROVED = "APPROVED";
	public static final String REJECTED = "REJECTED";
	private static final String VALIDATEAPPLICANTDOCUMENT = "mosip.regproc.packet.validator.validate-applicant-document";
    private static final String VALIDATEAPPLICANTDOCUMENTPROCESS = "mosip.regproc.packet.validator.validate-applicant-document.processes";

	@Autowired
	private PriorityBasedPacketManagerService packetManagerService;

	@Autowired
	private Utilities utility;

	@Autowired
	private Environment env;
	
	@Autowired
	ObjectMapper mapper;

	@Autowired
	private BiometricsXSDValidator biometricsXSDValidator;
	
	@Autowired
	private BiometricsSignatureValidator biometricsSignatureValidator;

	@Autowired
	private ApplicantDocumentValidation applicantDocumentValidation;

	@Value("${mosip.regproc.packet.validator.validate-update-renewal-nin:false}")
	private boolean isEnabled;

	@Value("${mosip.regproc.introducer-validator.firstid.age.limit:16}")
	private String firstIdAgelimit;

	@Value("${mosip.regproc.introducer-validator.renewal.age.limit:16}")
	private String RenewalAgelimit;

	@Value("${mosip.regproc.packet.validator.max.number.spouses:4}")
	private Integer maxNumberOfSpouses;
	
	@Value("${mosip.regproc.packet.validator.renewal.min-years-to-reapply:10}")
	private int minYearsBeforeRenewalAllowed;
	
	@Value("#{'${mosip.regproc.packet.validator.process:RENEWAL}'.split(',')}")
	private List<String> allowedProcess;

	@Autowired
	RegistrationRepositary<RegistrationStatusEntity, String> registrationStatusRepositary;
	
	@SuppressWarnings("unused")
	@Override
	public boolean validate(String id, String process, PacketValidationDto packetValidationDto)
			throws ApisResourceAccessException, RegistrationProcessorCheckedException, IOException,
			JsonProcessingException, PacketManagerException {
		String uin = null;
		try {
			ValidatePacketResponse response = packetManagerService.validate(id, process,
					ProviderStageName.PACKET_VALIDATOR);
			if (!response.isValid()) {
				regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), id,
						"ERROR =======>" + StatusUtil.PACKET_MANAGER_VALIDATION_FAILURE.getMessage());
				packetValidationDto
						.setPacketValidatonStatusCode(StatusUtil.PACKET_MANAGER_VALIDATION_FAILURE.getCode());
				packetValidationDto
						.setPacketValidaionFailureMessage(StatusUtil.PACKET_MANAGER_VALIDATION_FAILURE.getMessage());
				return false;
			}
			
			//Check consent
			if(!checkConsentForPacket(id,process,ProviderStageName.PACKET_VALIDATOR))
			{
				regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), id,
						"ERROR =======>" + StatusUtil.PACKET_CONSENT_VALIDATION.getMessage());
				packetValidationDto
						.setPacketValidatonStatusCode(StatusUtil.PACKET_CONSENT_VALIDATION.getCode());
				packetValidationDto
						.setPacketValidaionFailureMessage(StatusUtil.PACKET_CONSENT_VALIDATION.getMessage());
				return false;
			}
			


			if (process.equalsIgnoreCase(RegistrationType.UPDATE.toString())
					|| process.equalsIgnoreCase(RegistrationType.RES_UPDATE.toString())
					|| process.equalsIgnoreCase(RegistrationType.RENEWAL.toString())
					|| process.equalsIgnoreCase(RegistrationType.FIRSTID.toString())
					|| process.equalsIgnoreCase(RegistrationType.LOST.toString())) {
				uin = utility.getUINByHandle(id, process, ProviderStageName.PACKET_VALIDATOR);
				// In production we need to enable isEnabled property so added or condition
				if (uin != null || isEnabled) {
				if (uin == null) {
					regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString(), id,
							"ERROR =======>" + PlatformErrorMessages.RPR_PVM_INVALID_UIN.getMessage());
					throw new IdRepoAppException(PlatformErrorMessages.RPR_PVM_INVALID_UIN.getMessage());
				}
				JSONObject jsonObject = utility.retrieveIdrepoJson(uin);
				if (jsonObject == null) {
					regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString(), id,
							"ERROR =======>" + PlatformErrorMessages.RPR_PIS_IDENTITY_NOT_FOUND.getMessage());
					throw new IdRepoAppException(PlatformErrorMessages.RPR_PIS_IDENTITY_NOT_FOUND.getMessage());
				}
				if(process.equalsIgnoreCase(RegistrationType.RENEWAL.toString())){
					if (!validateAgeToRenewal(id, process, packetValidationDto)) {
						packetValidationDto.setPacketValidaionFailureMessage(StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_RENEWAL.getMessage());
						packetValidationDto.setPacketValidatonStatusCode(StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_RENEWAL.getCode());
						return false;
					}
				}
				
				//validation for Renewal application. 
				if(process.equalsIgnoreCase(RegistrationType.RENEWAL.toString())) {
					
					regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
				            LoggerFileConstant.REGISTRATIONID.toString(), id,
				            "INFO =======> Renewal validation started for registration");

				    regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
				            LoggerFileConstant.REGISTRATIONID.toString(), id,
				            "INFO =======> Minimum years before renewal allowed: " + minYearsBeforeRenewalAllowed);
					
					// Reject if Renewal application is PROCESSED less than 10 years, accept if >= 10 years
				    if (!validateRenewalExpiryDate(jsonObject, id, process)) {
				        regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
				                LoggerFileConstant.REGISTRATIONID.toString(), id,
				                "ERROR =======>" + StatusUtil.PVM_RENEWAL_NOT_ALLOWED_WITHIN_10_YEARS.getMessage());
				        packetValidationDto.setPacketValidaionFailureMessage(
				                "Renewal rejected - Not allowed within " + minYearsBeforeRenewalAllowed + " years");
				        packetValidationDto.setPacketValidatonStatusCode(StatusUtil.PVM_RENEWAL_NOT_ALLOWED_WITHIN_10_YEARS.getCode());
				        
				        regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
				                LoggerFileConstant.REGISTRATIONID.toString(), id,
				                "INFO =======> Renewal packet validation failed and response prepared");
				        
				        return false;
				    }
				    
				    regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
				            LoggerFileConstant.REGISTRATIONID.toString(), id,
				            "INFO =======> Renewal validation passed");
				}
				
				
				if (!checkNumberOfSpouses(jsonObject, id, process)) {
					packetValidationDto.setPacketValidaionFailureMessage(
							StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_ADD_SPOUSE.getMessage());
					packetValidationDto
							.setPacketValidatonStatusCode(StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_ADD_SPOUSE.getCode());
					return false;
				}
				String ChangeIncitizenshipTypeCop = packetManagerService.getField(id,MappingJsonConstants.CHANGE_APPLICANT_CITIZENSHIPTYPECOP, process, ProviderStageName.PACKET_VALIDATOR);
				if (ChangeIncitizenshipTypeCop!=null && "Y".equalsIgnoreCase(ChangeIncitizenshipTypeCop)){
						if (!isValidServiceTypeChange(jsonObject, id, process)) {
							packetValidationDto.setPacketValidaionFailureMessage(StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_USERSERVICETYPE.getMessage());
							packetValidationDto.setPacketValidatonStatusCode(StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_USERSERVICETYPE.getCode());
							return false;
						}
				}
				String status = utility.retrieveIdrepoJsonStatus(uin);
				if (process.equalsIgnoreCase(RegistrationType.UPDATE.toString())
						&& status.equalsIgnoreCase(RegistrationType.DEACTIVATED.toString())) {
					regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString(), id,
							"ERROR =======>" + PlatformErrorMessages.RPR_PVM_UPDATE_DEACTIVATED.getMessage());
					throw new RegistrationProcessorCheckedException(
							PlatformErrorMessages.RPR_PVM_UPDATE_DEACTIVATED.getCode(), "UIN is Deactivated");
				}

			// check if uin is in idrepisitory
			if (RegistrationType.UPDATE.name().equalsIgnoreCase(process)
					|| RegistrationType.RES_UPDATE.name().equalsIgnoreCase(process)
					|| process.equalsIgnoreCase(RegistrationType.RENEWAL.toString())
					|| process.equalsIgnoreCase(RegistrationType.FIRSTID.toString())) {

				if (!utility.uinPresentInIdRepo(String.valueOf(uin))) {
					regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString(), id,
							"ERROR =======>" + StatusUtil.UIN_NOT_FOUND_IDREPO.getMessage());
					packetValidationDto.setPacketValidaionFailureMessage(StatusUtil.UIN_NOT_FOUND_IDREPO.getMessage());
					packetValidationDto.setPacketValidatonStatusCode(StatusUtil.UIN_NOT_FOUND_IDREPO.getCode());
					return false;
				}
			}
			if (process.equalsIgnoreCase(RegistrationType.FIRSTID.toString())) {
				try {
					if (!validateAgeToGetCard(id, process, packetValidationDto)) {
						packetValidationDto.setPacketValidaionFailureMessage(
								StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_GETFIRSTID.getMessage());
						packetValidationDto.setPacketValidatonStatusCode(
								StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_GETFIRSTID.getCode());
						return false;
					}
					ResponseDTO responseDTO = utility.getIdrepoResponseByHandle(id, process,
							ProviderStageName.PACKET_VALIDATOR);
					boolean isValidFirstID = true;
					if (responseDTO != null) {
						List<CardDetailDto> cardDetailsDtoList = responseDTO.getCardDetails();
						for (CardDetailDto cardDetailDto : cardDetailsDtoList) {
							if (cardDetailDto.getCardNumber() != null && !cardDetailDto.getCardNumber().isEmpty()) {
								isValidFirstID = false;
								break;
							}
						}
						if (!isValidFirstID) {
							packetValidationDto
									.setPacketValidaionFailureMessage(StatusUtil.PVM_ALREADY_CARD_EXISTS.getMessage());
							packetValidationDto
									.setPacketValidatonStatusCode(StatusUtil.PVM_ALREADY_CARD_EXISTS.getCode());
							return false;
						}
					}
				} catch (Exception e) {
					// TODO this catch block need to be removed after complete migration
					regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString(), id,
							"ERROR =======>" + StatusUtil.UIN_NOT_FOUND_IDREPO.getMessage());
				}
			}

		}
		if (process.equalsIgnoreCase(RegistrationType.LOST.toString())) {
			String handle = packetManagerService.getFieldByMappingJsonKey(id, MappingJsonConstants.NIN, process,
					ProviderStageName.PACKET_VALIDATOR);
			if (StringUtils.isNotEmpty(handle)) {
				if (!validateAgeToGetCard(id, process, packetValidationDto)) {
					packetValidationDto.setPacketValidaionFailureMessage(
							StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_LOST.getMessage());
					packetValidationDto
							.setPacketValidatonStatusCode(StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_LOST.getCode());
					return false;
				}
			}
		}
		}
	

			// document validation
			if (!applicantDocumentValidation(id, process, packetValidationDto)) {
				regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), id,
						"ERROR =======>" + StatusUtil.APPLICANT_DOCUMENT_VALIDATION_FAILED.getMessage());
				return false;
			}



			if (!biometricsXSDValidation(id, process, packetValidationDto)) {
				return false;
			}
		} catch (PacketManagerException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					id, RegistrationStatusCode.FAILED.toString() + e.getMessage() + ExceptionUtils.getStackTrace(e));
			throw e;
		} catch (JSONException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					id, RegistrationStatusCode.FAILED.toString() + e.getMessage() + ExceptionUtils.getStackTrace(e));
			packetValidationDto.setPacketValidaionFailureMessage(
					StatusUtil.JSON_PARSING_EXCEPTION.getMessage() + e.getMessage());
			packetValidationDto.setPacketValidatonStatusCode(StatusUtil.JSON_PARSING_EXCEPTION.getCode());
		}

		packetValidationDto.setValid(true);
		return packetValidationDto.isValid();
	}

	private boolean biometricsXSDValidation(String id, String process, PacketValidationDto packetValidationDto)
			throws ApisResourceAccessException, PacketManagerException, JsonProcessingException, IOException,
			RegistrationProcessorCheckedException, JSONException {
		List<String> fields = Arrays.asList(MappingJsonConstants.INDIVIDUAL_BIOMETRICS,
				MappingJsonConstants.AUTHENTICATION_BIOMETRICS, MappingJsonConstants.INTRODUCER_BIO,
				MappingJsonConstants.OFFICERBIOMETRICFILENAME, MappingJsonConstants.SUPERVISORBIOMETRICFILENAME);
		
		Map<String, String> metaInfoMap = packetManagerService.getMetaInfo(id, process,
				ProviderStageName.PACKET_VALIDATOR);
		
		for (String field : fields) {
			BiometricRecord biometricRecord = null;
			if (field.equals(MappingJsonConstants.OFFICERBIOMETRICFILENAME)
					|| field.equals(MappingJsonConstants.SUPERVISORBIOMETRICFILENAME)) {
				String value = getOperationsDataFromMetaInfo(id, process, field, metaInfoMap);
				if (value != null && !value.isEmpty()) {
					biometricRecord = packetManagerService.getBiometrics(id, field, process,
							ProviderStageName.PACKET_VALIDATOR);
				}
			} else {
				String value = packetManagerService.getField(id, field, process, ProviderStageName.PACKET_VALIDATOR);
				if (value != null && !value.isEmpty()) {
					biometricRecord = packetManagerService.getBiometricsByMappingJsonKey(id, field, process,
							ProviderStageName.PACKET_VALIDATOR);
				}
			}

			if (biometricRecord != null) {
				try {
					biometricsXSDValidator.validateXSD(biometricRecord);
					biometricsSignatureValidator.validateSignature(id, process, biometricRecord, metaInfoMap);
				} catch (Exception e) {
					if (e instanceof CbeffException) {
						regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
								LoggerFileConstant.REGISTRATIONID.toString(), id,
								"ERROR =======> " + StatusUtil.XSD_VALIDATION_EXCEPTION.getMessage());
						packetValidationDto.setPacketValidaionFailureMessage(
								StatusUtil.XSD_VALIDATION_EXCEPTION.getMessage());
						packetValidationDto.setPacketValidatonStatusCode(StatusUtil.XSD_VALIDATION_EXCEPTION.getCode());
						return false;
					} else if (e instanceof BiometricSignatureValidationException) {
						regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
								LoggerFileConstant.REGISTRATIONID.toString(), id,
								"ERROR =======> " + StatusUtil.BIOMETRICS_SIGNATURE_VALIDATION_FAILURE.getMessage());
						packetValidationDto.setPacketValidaionFailureMessage(
								StatusUtil.BIOMETRICS_SIGNATURE_VALIDATION_FAILURE.getMessage() + "--> "
										+ e.getMessage());
						packetValidationDto.setPacketValidatonStatusCode(
								StatusUtil.BIOMETRICS_SIGNATURE_VALIDATION_FAILURE.getCode());
						return false;
					} else {
						throw new RegistrationProcessorCheckedException(
								PlatformErrorMessages.RPR_SYS_IO_EXCEPTION.getCode(),
								PlatformErrorMessages.RPR_SYS_IO_EXCEPTION.getMessage(), e);
					}
				}
			}
		}
		return true;
	}
	
	private String getOperationsDataFromMetaInfo(String id, String process, String fileName,
			Map<String, String> metaInfoMap)
			throws ApisResourceAccessException, PacketManagerException, IOException, JSONException {
		String metadata = metaInfoMap.get(JsonConstant.OPERATIONSDATA);
		String value = null;
		if (StringUtils.isNotEmpty(metadata)) {
			JSONArray jsonArray = new JSONArray(metadata);

			for (int i = 0; i < jsonArray.length(); i++) {
				if (!jsonArray.isNull(i)) {
					org.json.JSONObject jsonObject = (org.json.JSONObject) jsonArray.get(i);
					FieldValue fieldValue = mapper.readValue(jsonObject.toString(), FieldValue.class);
					if (fieldValue.getLabel().equalsIgnoreCase(fileName)) {
						value = fieldValue.getValue();
						break;
					}
				}
			}
		}
		return value;
	}


	private boolean applicantDocumentValidation(String registrationId, String process,
			PacketValidationDto packetValidationDto)
			throws ApisResourceAccessException, JsonProcessingException, PacketManagerException, IOException {
		String validateApplicant=env.getProperty(VALIDATEAPPLICANTDOCUMENT);
		if (validateApplicant!=null && validateApplicant.trim().equalsIgnoreCase(VALIDATIONFALSE))
			return true;
		else {
			String validateApplicantDocument=env.getProperty(VALIDATEAPPLICANTDOCUMENTPROCESS);
			if(validateApplicantDocument!=null && validateApplicantDocument.contains(process)) {
				boolean result = applicantDocumentValidation.validateDocument(registrationId, process);
				if (!result) {
					packetValidationDto.setPacketValidaionFailureMessage(StatusUtil.APPLICANT_DOCUMENT_VALIDATION_FAILED.getMessage());
					packetValidationDto.setPacketValidatonStatusCode(StatusUtil.APPLICANT_DOCUMENT_VALIDATION_FAILED.getCode());
				}
				return result;
			}
			return true;
		}
	}
	
	private boolean checkConsentForPacket(String id, String process, ProviderStageName stageName)
			throws ApisResourceAccessException, PacketManagerException, JsonProcessingException, IOException {

		String val = packetManagerService.getField(id, MappingJsonConstants.CONSENT, process, stageName);
		if (null!=val && StringUtils.isNotEmpty(val)) {
			if (val.equalsIgnoreCase("N"))
				return false;
		}
		return true;

	}

	private boolean validateAgeToGetCard(String id, String process, PacketValidationDto packetValidationDto)
			throws ApisResourceAccessException, JsonProcessingException, PacketManagerException, IOException {
			double age = utility.getApplicantAge(id, process,
					ProviderStageName.PACKET_VALIDATOR);
			int ageThreshold = Integer.parseInt(firstIdAgelimit);
			if (age < ageThreshold) {

				return false;
			}
			return true;

	}

	private boolean validateAgeToRenewal(String id, String process, PacketValidationDto packetValidationDto)
			throws ApisResourceAccessException, JsonProcessingException, PacketManagerException, IOException {
		double age = utility.getApplicantAge(id, process,
				ProviderStageName.PACKET_VALIDATOR);
		int ageThreshold = Integer.parseInt(RenewalAgelimit);
		if (age < ageThreshold) {

			return false;
		}
		return true;

	}
	
	private boolean checkNumberOfSpouses(JSONObject jsonObject, String id, String process)
			throws ApisResourceAccessException, PacketManagerException, JsonProcessingException, IOException {
		boolean isValidNumberOfSpouse = true;
		String numberOfOtherSpousesInDb = JsonUtil.getJSONValue(jsonObject, MappingJsonConstants.NUMBEROFOTHERSPOUSES);
		if (numberOfOtherSpousesInDb != null) {
			int numberOfOtherSpousesInDbValue = Integer.parseInt(numberOfOtherSpousesInDb);
			String numberOfOtherSpousesInPacket = packetManagerService.getFieldByMappingJsonKey(
					id, MappingJsonConstants.NUMBEROFOTHERSPOUSES,
					process, ProviderStageName.PACKET_VALIDATOR);
			if (numberOfOtherSpousesInPacket != null) {
				int numberOfOtherSpousesInPacketValue = Integer.parseInt(numberOfOtherSpousesInPacket);
				if (numberOfOtherSpousesInDbValue < maxNumberOfSpouses) {
					int leftOutSpouses = maxNumberOfSpouses - numberOfOtherSpousesInDbValue;
					if (numberOfOtherSpousesInPacketValue > leftOutSpouses) {
						isValidNumberOfSpouse = false;
					}
				} else if (numberOfOtherSpousesInDbValue >= maxNumberOfSpouses) {
					isValidNumberOfSpouse = false;
				}
			}
		}
		return isValidNumberOfSpouse;
	}
	private boolean isValidServiceTypeChange(JSONObject jsonObject, String id, String process)
			throws ApisResourceAccessException, PacketManagerException, JsonProcessingException, IOException {

		ObjectMapper objectMapper = new ObjectMapper();

		Object userServiceTypeInDb = JsonUtil.getJSONValue(jsonObject, MappingJsonConstants.APPLICANT_CITIZENSHIPTYPE);
		Object citizenshipTypeCop = packetManagerService.getField(id, MappingJsonConstants.CHANGE_IN_APPLICANT_CITIZENSHIPTYPE, process, ProviderStageName.PACKET_VALIDATOR);

		try {
			// Convert JSON objects to lists
			List<Map<String, String>> userServiceList = objectMapper.readValue(
					userServiceTypeInDb.toString(), new TypeReference<>() {});
			List<Map<String, String>> citizenshipTypeList = objectMapper.readValue(
					citizenshipTypeCop.toString(), new TypeReference<>() {});

			// Extract values if lists are non-empty
			Optional<String> serviceTypeOpt = userServiceList.stream().findFirst().map(map -> map.get("value"));
			Optional<String> citizenshipTypeOpt = citizenshipTypeList.stream().findFirst().map(map -> map.get("value"));

			if (serviceTypeOpt.isEmpty() || citizenshipTypeOpt.isEmpty()) {
				return false;
			}

			String serviceType = serviceTypeOpt.get();
			String citizenshipType = citizenshipTypeOpt.get();

			// Validate service type change
			switch (serviceType) {
				case "By Birth /Descent":
					return citizenshipType.equalsIgnoreCase("Citizenship by Naturalization") ||
							citizenshipType.equalsIgnoreCase("Citizenship by Registration") ||
							citizenshipType.equalsIgnoreCase("Dual citizenship");

				case "By Registration":
					return citizenshipType.equalsIgnoreCase("Dual citizenship");

				case "By Naturalization":
					return citizenshipType.equalsIgnoreCase("Dual citizenship");

				case "Citizenship under the Article 9":
					return citizenshipType.equalsIgnoreCase("Dual citizenship");

				default:
					System.out.println("Unknown/Invalid service type: " + serviceType);
					return false;
			}
		} catch (Exception e) {
			System.err.println("Error processing service type change validation: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}
	
	private boolean validateRenewalExpiryDate(JSONObject jsonObject, String id, String process)
			throws ApisResourceAccessException, PacketManagerException, JsonProcessingException, IOException {
		boolean isValidRenewalExpiry = true;
		try {
			regProcLogger.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					id, "Starting renewal expiry validation for registration ID: " + id);
			
			Optional<String> referenceId = registrationStatusRepositary.getReferenceIdByRegId(id);
			if (referenceId.isPresent()) {
				regProcLogger.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
						id, "Reference ID found: " + referenceId.get());
				
				LocalDateTime thresholdDate = LocalDateTime.now().minusYears(minYearsBeforeRenewalAllowed);
				regProcLogger.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
						id, "Threshold date calculated: " + thresholdDate + " (minYearsBeforeRenewalAllowed: " + minYearsBeforeRenewalAllowed + ")");
				
				List<Map<String, Object>> otherRegistrations = registrationStatusRepositary
						.getRegIdAndStatusByReferenceId(referenceId.get(), id, allowedProcess,
								thresholdDate);
				regProcLogger.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
						id, "Retrieved " + otherRegistrations.size() + " other registrations for reference ID: " + referenceId.get());
				
				boolean hasInvalidStatus = otherRegistrations.stream().anyMatch(reg -> {
					String statusCode = (String) reg.get("statusCode");
					return statusCode != null
							&& (statusCode.equalsIgnoreCase(RegistrationTransactionStatusCode.PROCESSED.toString()) || statusCode.equalsIgnoreCase(RegistrationTransactionStatusCode.PROCESSING.toString())
									|| statusCode.equalsIgnoreCase(RegistrationTransactionStatusCode.RESUMABLE.toString())
									|| statusCode.equalsIgnoreCase(RegistrationTransactionStatusCode.REPROCESS.toString()));
				});
				isValidRenewalExpiry = !hasInvalidStatus;
				
				regProcLogger.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
						id, "Renewal expiry validation result - hasInvalidStatus: " + hasInvalidStatus + ", isValidRenewalExpiry: " + isValidRenewalExpiry);
				
				return isValidRenewalExpiry;
			}
		} catch (DateTimeParseException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					id, "Error parsing dateOfExpiry: " + e.getMessage());
			isValidRenewalExpiry = false;
		} catch (Exception e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					id, "Error processing the Renewal request : " + e.getMessage());
			isValidRenewalExpiry = false;
		}
		return isValidRenewalExpiry;
	}
}
