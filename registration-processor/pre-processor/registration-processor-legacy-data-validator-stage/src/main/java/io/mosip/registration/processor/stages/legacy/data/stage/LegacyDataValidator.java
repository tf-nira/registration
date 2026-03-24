package io.mosip.registration.processor.stages.legacy.data.stage;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.bind.JAXBException;

import org.json.JSONException;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.code.RegistrationTransactionStatusCode;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.constant.RegistrationType;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.DataMigrationException;
import io.mosip.registration.processor.core.exception.LegacyDataBiomtericException;
import io.mosip.registration.processor.core.exception.LegacyDataValidationException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.exception.ValidationFailedException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.exception.util.PlatformSuccessMessages;
import io.mosip.registration.processor.core.logger.LogDescription;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.core.status.util.StatusUtil;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.core.util.RegistrationExceptionMapperUtil;
import io.mosip.registration.processor.packet.storage.exception.ParsingException;
import io.mosip.registration.processor.packet.storage.utils.PacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.PriorityBasedPacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.service.RegistrationStatusService;

@Service
public class LegacyDataValidator {
	private static Logger regProcLogger = RegProcessorLogger.getLogger(LegacyDataValidator.class);

	public static final String INDIVIDUAL_TYPE_UIN = "UIN";
	@Autowired
	RegistrationExceptionMapperUtil registrationExceptionMapperUtil;

	@Autowired
	private PriorityBasedPacketManagerService priorityBasedPacketManagerService;

	@Autowired
	private PacketManagerService packetManagerService;


	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

	@Autowired
	ObjectMapper mapper;

	@Autowired
	private Utilities utility;
	
	@Autowired
	private RegistrationProcessorRestClientService<Object> restApi;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private LegacyValidationUtility legacyValidationUtility;

	@Value("${mosip.regproc.legacydata.validator.tpi.username}")
	private String username;
	
	@Value("${mosip.regproc.introducer-validator.firstid.age.limit:16}")
	private String firstIdAgelimit;
	
	@Value("${mosip.regproc.packet.classifier.tagging.not-available-tag-value}")
	private String notAvailableTagValue;
	
	/** The dob format. */
	@Value("${registration.processor.applicant.dob.format}")
	private String dobFormat;
	
	@Value("${mosip.regproc.introducer-validator.renewal.age.limit:16}")
	private String renewalAgelimit;

	public void validate(String registrationId, InternalRegistrationStatusDto registrationStatusDto,
			LogDescription description, MessageDTO object)
			throws ApisResourceAccessException, PacketManagerException, JsonProcessingException, IOException,
			ValidationFailedException, JAXBException, NoSuchAlgorithmException, NumberFormatException, JSONException,
			DataMigrationException, LegacyDataBiomtericException, LegacyDataValidationException {

		regProcLogger.debug("validate called for registrationId {}", registrationId);

		String NIN =  priorityBasedPacketManagerService.getFieldByMappingJsonKey(registrationId,
				MappingJsonConstants.NIN, registrationStatusDto.getRegistrationType(),
		ProviderStageName.LEGACY_DATA_VALIDATOR);

		JSONObject jSONObject = utility.getIdentityJSONObjectByHandle(NIN);
		
		if (jSONObject == null) {
			regProcLogger.info("NIN not available in idrepo: {}",
					registrationId);
				registrationStatusDto.setLatestTransactionStatusCode(
										RegistrationTransactionStatusCode.ON_HOLD.toString());
								registrationStatusDto.setStatusComment(
										NIN + " -- not available in Idrepo");
								registrationStatusDto
										.setSubStatusCode(StatusUtil.NIN_NOT_AVAILABLE_IN_IDREPO.getCode());
								registrationStatusDto.setStatusCode(RegistrationStatusCode.ON_HOLD.toString());

								description.setMessage(
										PlatformErrorMessages.RPR_NIN_NOT_AVAILABLE_FAILED.getMessage()
												+ " -- " + registrationId);
								description.setCode(
										PlatformErrorMessages.RPR_NIN_NOT_AVAILABLE_FAILED.getCode());
								object.setOnHold(true);

		} else {
			regProcLogger.info("NIN is present in mosip system : {}", registrationId);
			object.setOnHold(false);
			Map<String, String> tags = new HashMap<>();
			tags = object.getTags();
			boolean getFirstIdAgeValidFlag = true;
			boolean isValidCOP = true;
			boolean isValidRenewal = true;
			String registrationType = registrationStatusDto.getRegistrationType();

			if (tags.get("AGE_GROUP") == null
					|| tags.get("AGE_GROUP").equalsIgnoreCase(notAvailableTagValue)) {
				Map<String, String> ageTags = legacyValidationUtility.generateAgeTags(registrationId,
						registrationType);
				packetManagerService.addOrUpdateTags(registrationId, ageTags);
				tags.putAll(ageTags);
			}
			object.setTags(tags);
			//age check validation for get first id
			if(registrationType.equalsIgnoreCase(RegistrationType.FIRSTID.toString())) {
				String dateOfBirth = jSONObject.get("dateOfBirth").toString();
				if (dateOfBirth != null) {
					int age = calculateAge(dateOfBirth);
					int ageThreshold = Integer.parseInt(firstIdAgelimit);
					if (age < ageThreshold)
						getFirstIdAgeValidFlag = false;
					else
						getFirstIdAgeValidFlag = true;
				}else {
					getFirstIdAgeValidFlag = false;
				}
			}
			if(registrationType.equalsIgnoreCase(RegistrationType.UPDATE.toString())){
				String ChangeIncitizenshipTypeCop = packetManagerService.getField(registrationId,MappingJsonConstants.CHANGE_APPLICANT_CITIZENSHIPTYPECOP, registrationType, ProviderStageName.LEGACY_DATA_VALIDATOR);
				if (ChangeIncitizenshipTypeCop!=null && "Y".equalsIgnoreCase(ChangeIncitizenshipTypeCop)){
					isValidCOP = isValidServiceTypeChange(jSONObject, registrationId, registrationType);
					if (!isValidCOP) {
						description.setMessage(StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_USERSERVICETYPE.getMessage());
						description.setCode(StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_USERSERVICETYPE.getCode());
					}
				}
				isValidCOP = legacyValidationUtility.checkNumberOfSpouses(jSONObject, registrationId, registrationType);
				if (!isValidCOP) {
					description.setMessage(StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_USERSERVICETYPE.getMessage());
					description.setCode(StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_USERSERVICETYPE.getCode());
				}
			}
			if (registrationType.equalsIgnoreCase(RegistrationType.RENEWAL.toString())) {
				isValidRenewal = validateAgeToRenewal(jSONObject, registrationId, registrationType);
			}
					
			if(!getFirstIdAgeValidFlag){
				tags.put("META_INFO-META_DATA-registrationType",notAvailableTagValue);
				description.setMessage(
						StatusUtil.LEGACY_DATA_VALIDATION_FAILED_GETFIRSTID.getMessage());
				description.setCode(
						StatusUtil.LEGACY_DATA_VALIDATION_FAILED_GETFIRSTID.getCode());
			}
			if(!isValidCOP) {
				tags.put("META_INFO-META_DATA-registrationType",notAvailableTagValue);
			}
			if (!isValidRenewal) {
				tags.put("META_INFO-META_DATA-registrationType", notAvailableTagValue);
				description.setMessage(StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_RENEWAL.getMessage());
				description.setCode(StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_RENEWAL.getCode());
			}

			if(tags.get("META_INFO-META_DATA-registrationType").equalsIgnoreCase(notAvailableTagValue)) {
				object.setIsValid(false);
				throw new ValidationFailedException(description.getCode(),
						description.getMessage());
			}
			else{
				registrationStatusDto.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.SUCCESS.toString());
				registrationStatusDto.setStatusComment(StatusUtil.LEGACY_DATA_VALIDATION_SUCCESS.getMessage());
				registrationStatusDto.setSubStatusCode(StatusUtil.LEGACY_DATA_VALIDATION_SUCCESS.getCode());
				registrationStatusDto.setStatusCode(RegistrationStatusCode.PROCESSING.toString());

				description.setMessage(
						PlatformSuccessMessages.RPR_LEGACY_DATA_VALIDATE.getMessage() + " -- " + registrationId);
				description.setCode(PlatformSuccessMessages.RPR_LEGACY_DATA_VALIDATE.getCode());
			}
		}

		regProcLogger.debug("validate call ended for registrationId {}", registrationId);

	}
		private int calculateAge(String applicantDob) {
			regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
					"Utilities::calculateAge():: entry");

			DateFormat sdf = new SimpleDateFormat(dobFormat);
			Date birthDate = null;
			try {
				birthDate = sdf.parse(applicantDob);

			} catch (ParseException e) {
				regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
						"", "Utilities::calculateAge():: error with error message "
								+ PlatformErrorMessages.RPR_SYS_PARSING_DATE_EXCEPTION.getMessage());
				throw new ParsingException(PlatformErrorMessages.RPR_SYS_PARSING_DATE_EXCEPTION.getCode(), e);
			}
			LocalDate ld = new java.sql.Date(birthDate.getTime()).toLocalDate();
			Period p = Period.between(ld, LocalDate.now());
			regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
					"Utilities::calculateAge():: exit");

			return p.getYears();

		}

	private boolean isValidServiceTypeChange(JSONObject jsonObject, String id, String process)
			throws ApisResourceAccessException, PacketManagerException, JsonProcessingException, IOException {

		ObjectMapper objectMapper = new ObjectMapper();

		Object userServiceTypeInDb = JsonUtil.getJSONValue(jsonObject, MappingJsonConstants.APPLICANT_CITIZENSHIPTYPE);
		Object citizenshipTypeCop = packetManagerService.getField(id, MappingJsonConstants.CHANGE_IN_APPLICANT_CITIZENSHIPTYPE, process, ProviderStageName.LEGACY_DATA_VALIDATOR);

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

	public boolean validateAgeToRenewal(JSONObject jsonObject, String id, String process)
			throws ApisResourceAccessException, JsonProcessingException, PacketManagerException, IOException {
		boolean renewalAgeValidFlag = true;
		String dateOfBirth = jsonObject.get("dateOfBirth").toString();
		if (dateOfBirth != null) {
			int age = calculateAge(dateOfBirth);
			int ageThreshold = Integer.parseInt(renewalAgelimit);
			if (age < ageThreshold)
				renewalAgeValidFlag = false;
			else
				renewalAgeValidFlag = true;
		} else {
			renewalAgeValidFlag = false;
		}

		return renewalAgeValidFlag;

	}
}
