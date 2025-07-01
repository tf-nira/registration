package io.mosip.registration.processor.stages.legacy.data.stage;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.json.JSONException;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.code.ApiName;
import io.mosip.registration.processor.core.code.ModuleName;
import io.mosip.registration.processor.core.code.RegistrationTransactionStatusCode;
import io.mosip.registration.processor.core.code.RegistrationTransactionTypeCode;
import io.mosip.registration.processor.core.common.rest.dto.ErrorDTO;
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
import io.mosip.registration.processor.core.http.RequestWrapper;
import io.mosip.registration.processor.core.http.ResponseWrapper;
import io.mosip.registration.processor.core.logger.LogDescription;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.migration.dto.MigrationOnDemandResponse;
import io.mosip.registration.processor.core.migration.dto.MigrationRequestDto;
import io.mosip.registration.processor.core.migration.dto.MigrationResponse;
import io.mosip.registration.processor.core.packet.dto.DocumentDto;
import io.mosip.registration.processor.core.packet.dto.PacketDto;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.core.status.util.StatusUtil;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.core.util.RegistrationExceptionMapperUtil;
import io.mosip.registration.processor.packet.storage.dto.Document;
import io.mosip.registration.processor.packet.storage.dto.FieldResponseDto;
import io.mosip.registration.processor.packet.storage.exception.ParsingException;
import io.mosip.registration.processor.packet.storage.utils.FingrePrintConvertor;
import io.mosip.registration.processor.packet.storage.utils.IdSchemaUtil;
import io.mosip.registration.processor.packet.storage.utils.LegacyDataApiUtility;
import io.mosip.registration.processor.packet.storage.utils.PacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.PriorityBasedPacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.stages.legacy.data.dto.Body;
import io.mosip.registration.processor.stages.legacy.data.dto.Envelope;
import io.mosip.registration.processor.stages.legacy.data.dto.Fingerprint;
import io.mosip.registration.processor.stages.legacy.data.dto.Header;
import io.mosip.registration.processor.stages.legacy.data.dto.Password;
import io.mosip.registration.processor.stages.legacy.data.dto.Position;
import io.mosip.registration.processor.stages.legacy.data.dto.Request;
import io.mosip.registration.processor.stages.legacy.data.dto.TransactionStatus;
import io.mosip.registration.processor.stages.legacy.data.dto.UsernameToken;
import io.mosip.registration.processor.stages.legacy.data.dto.VerifyPerson;
import io.mosip.registration.processor.stages.legacy.data.dto.VerifyPersonResponse;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.dto.SyncRegistrationDto;
import io.mosip.registration.processor.status.dto.SyncResponseDto;
import io.mosip.registration.processor.status.entity.SyncRegistrationEntity;
import io.mosip.registration.processor.status.service.RegistrationStatusService;
import io.mosip.registration.processor.status.service.SyncRegistrationService;
import io.mosip.registration.processor.status.utilities.RegistrationUtility;

@Service
public class LegacyDataValidator {
	private static Logger regProcLogger = RegProcessorLogger.getLogger(LegacyDataValidator.class);

	public static final String INDIVIDUAL_TYPE_UIN = "UIN";

	private static final String ID = "mosip.commmons.packetmanager";
	private static final String VERSION = "v1";

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
	private LegacyDataApiUtility legacyDataApiUtility;

	@Autowired
	private RegistrationProcessorRestClientService<Object> restApi;

	@Autowired
	private SyncRegistrationService<SyncResponseDto, SyncRegistrationDto> syncRegistrationService;

	@Autowired
	private IdSchemaUtil idSchemaUtil;

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
	
	public void validate(String registrationId, InternalRegistrationStatusDto registrationStatusDto,
			LogDescription description, MessageDTO object)
			throws IOException, JAXBException, NoSuchAlgorithmException, NumberFormatException, JSONException,
			BaseCheckedException {

		regProcLogger.debug("validate called for registrationId {}", registrationId);

		String NIN =  priorityBasedPacketManagerService.getFieldByMappingJsonKey(registrationId,
				MappingJsonConstants.NIN, registrationStatusDto.getRegistrationType(),
		ProviderStageName.LEGACY_DATA_VALIDATOR);

		JSONObject jSONObject = utility.getIdentityJSONObjectByHandle(NIN);
		
		if (jSONObject == null) {
			regProcLogger.info("call for ondemand migration Started: {}",
					registrationId);
			MigrationRequestDto migrationRequestDto = new MigrationRequestDto();
			migrationRequestDto.setNin(NIN.toUpperCase());
			migrationRequestDto.setDependentRid(registrationId);
			RequestWrapper<MigrationRequestDto> requestWrapper = new RequestWrapper();
			requestWrapper.setRequest(migrationRequestDto);
			ResponseWrapper responseWrapper = (ResponseWrapper<?>) restApi
					.postApi(ApiName.MIGARTION_URL_NEW, "", "", requestWrapper, ResponseWrapper.class,
							null);
			if (responseWrapper.getErrors() != null && responseWrapper.getErrors().size() > 0) {
				regProcLogger.error("Error from migration api : {}{}", registrationId,
						JsonUtils.javaObjectToJsonString(responseWrapper));
				ErrorDTO error = (ErrorDTO) responseWrapper.getErrors().get(0);
				throw new DataMigrationException(error.getErrorCode(), error.getMessage());
			}
			else{
				MigrationResponse migrationResponse = objectMapper.readValue(
							JsonUtils.javaObjectToJsonString(responseWrapper.getResponse()),
							MigrationResponse.class);
				registrationStatusDto.setLatestTransactionStatusCode(
										RegistrationTransactionStatusCode.ON_HOLD.toString());
								registrationStatusDto.setStatusComment(
										StatusUtil.ON_DEMAND_PACKET_CREATION_SUCCESS.getMessage() + " and rid is "
												+ migrationResponse.getRid());
								registrationStatusDto
										.setSubStatusCode(StatusUtil.ON_DEMAND_PACKET_CREATION_SUCCESS.getCode());
								registrationStatusDto.setStatusCode(RegistrationStatusCode.ON_HOLD.toString());

								description.setMessage(
										PlatformSuccessMessages.RPR_LEGACY_DATA_VALIDATE_ONDEMAND_PACKET.getMessage()
												+ " -- " + registrationId);
								description.setCode(
										PlatformSuccessMessages.RPR_LEGACY_DATA_VALIDATE_ONDEMAND_PACKET.getCode());
			}
		} else {
			regProcLogger.info("NIN is present in mosip system : {}", registrationId);
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
				tags.putAll(ageTags);
			}
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
				isValidRenewal = legacyValidationUtility.validateAgeToRenewal(registrationId, registrationType);
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
				updatePacketStatus(registrationId, registrationStatusDto, description, null);
				object.setIsValid(false);
				throw new ValidationFailedException(StatusUtil.LEGACY_DATA_VALIDATION_FAILED.getMessage(),
						StatusUtil.LEGACY_DATA_VALIDATION_FAILED.getCode());
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

	private void updatePacketStatus(String registrationId, InternalRegistrationStatusDto registrationStatusDto,
			LogDescription description, SyncRegistrationEntity syncRegistrationEntityForOndemand) {
		regProcLogger.error("Validation Failed for : {}, {}", registrationId,
				description.getMessage());
		registrationStatusDto.setLatestTransactionStatusCode(
				RegistrationTransactionStatusCode.REJECTED.toString());
		if (syncRegistrationEntityForOndemand != null) {
			registrationStatusDto.setStatusComment(
					description.getMessage() + "  " + StatusUtil.ON_DEMAND_PACKET_CREATION_SUCCESS.getMessage()
							+ " and rid is " + syncRegistrationEntityForOndemand.getRegistrationId());
		} else {
			registrationStatusDto.setStatusComment(description.getMessage());
		}

		registrationStatusDto
				.setSubStatusCode(description.getCode());
		registrationStatusDto.setStatusCode(RegistrationStatusCode.REJECTED.toString());
	}

	private SyncRegistrationEntity createSyncAndRegistration(PacketDto packetDto, String stageName) {
		SyncRegistrationEntity syncRegistrationEntity = createSyncEntity(packetDto);
		syncRegistrationEntity = syncRegistrationService.saveSyncRegistrationEntity(syncRegistrationEntity);
		regProcLogger.info("Successfully sync the ondemand packet : {} {}", packetDto.getId());
		createRegistrationStatusEntity(stageName, syncRegistrationEntity);
		return syncRegistrationEntity;
	}

	private SyncRegistrationEntity createSyncEntity(PacketDto packetDto) {
		SyncRegistrationEntity syncRegistrationEntity = new SyncRegistrationEntity();
		syncRegistrationEntity.setRegistrationId(packetDto.getId().trim());
		syncRegistrationEntity.setLangCode("eng");
		syncRegistrationEntity.setRegistrationType(packetDto.getProcess());
		syncRegistrationEntity.setPacketHashValue("0");
		syncRegistrationEntity.setPacketSize(new BigInteger("0"));
		syncRegistrationEntity.setSupervisorStatus("APPROVED");
		syncRegistrationEntity.setPacketId(packetDto.getId());
		syncRegistrationEntity.setReferenceId(packetDto.getRefId());
		syncRegistrationEntity.setCreatedBy("MOSIP");
		syncRegistrationEntity.setCreateDateTime(LocalDateTime.now(ZoneId.of("UTC")));
		syncRegistrationEntity.setWorkflowInstanceId(RegistrationUtility.generateId());
		syncRegistrationEntity.setIsDeleted(false);
		return syncRegistrationEntity;
	}

	private PacketDto createOnDemandPacket(MigrationResponse migrationResponse,
			InternalRegistrationStatusDto registrationStatusDto, Map<String, String> tags, LogDescription description)
			throws ApisResourceAccessException,
			PacketManagerException,
			JsonProcessingException, IOException, NumberFormatException, JSONException, DataMigrationException {

		boolean getFirstIdAgeValidFlag = true;
		boolean isValidCOP = true;
		String registrationId = registrationStatusDto.getRegistrationId();
		String registrationType = registrationStatusDto.getRegistrationType();
		regProcLogger.info("Getting details to create ondemand packet : {}", registrationId);
		String schemaVersion = priorityBasedPacketManagerService.getFieldByMappingJsonKey(registrationStatusDto.getRegistrationId(),
				MappingJsonConstants.IDSCHEMA_VERSION, registrationType, ProviderStageName.LEGACY_DATA_VALIDATOR);

		Map<String, BiometricRecord> biometrics = getBiometrics(registrationId, registrationType);
		List<FieldResponseDto> audits = priorityBasedPacketManagerService.getAudits(registrationId, registrationType,
				ProviderStageName.LEGACY_DATA_VALIDATOR);
		List<Map<String, String>> auditList = new ArrayList<>();
		for (FieldResponseDto dto : audits) {
			auditList.add(dto.getFields());
		}
		Map<String, String> metaInfo = priorityBasedPacketManagerService.getMetaInfo(registrationId, registrationType,
				ProviderStageName.LEGACY_DATA_VALIDATOR);
		regProcLogger.info("successfully got  details to create ondemand packet : {}", registrationId);
		SyncRegistrationEntity regEntity = syncRegistrationService
				.findByWorkflowInstanceId(registrationStatusDto.getWorkflowInstanceId());
		Map<String, String> demographics = new HashMap<String, String>();
		if (migrationResponse.getDemographics() == null || migrationResponse.getDemographics().isEmpty()) {
			throw new DataMigrationException(StatusUtil.DATA_MIGRATION_DATA_ISSUE.getCode(),
					StatusUtil.DATA_MIGRATION_DATA_ISSUE.getMessage());
		}
		if (migrationResponse.getDocuments() == null || migrationResponse.getDocuments().isEmpty()) {
			throw new DataMigrationException(StatusUtil.DATA_MIGRATION_DATA_ISSUE.getCode(),
					StatusUtil.DATA_MIGRATION_DATA_ISSUE.getMessage());
		}
		if (migrationResponse.getDemographics() != null) {
			demographics.putAll(migrationResponse.getDemographics());
		}
		Map<String, DocumentDto> documents = new HashMap<String, DocumentDto>();
		if (migrationResponse.getDocuments() != null) {
			documents.putAll(migrationResponse.getDocuments());
		}
		
		//age check validation for get first id 
		if(registrationType.equalsIgnoreCase(RegistrationType.FIRSTID.toString())) {
			String dateOfBirth = demographics.get("dateOfBirth");
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
				JSONObject demographicsJson = new JSONObject(demographics);
				isValidCOP = isValidServiceTypeChange(demographicsJson, registrationId, registrationType);
			}
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
			description.setMessage(
					StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_USERSERVICETYPE.getMessage());
			description.setCode(
					StatusUtil.PVM_APPLICANT_NOT_ELIGIBLE_USERSERVICETYPE.getCode());
		}
		if((registrationType.equalsIgnoreCase(RegistrationType.RENEWAL.toString())) || (registrationType.equalsIgnoreCase(RegistrationType.UPDATE.toString()) && isValidCOP) || ((registrationType.equalsIgnoreCase(RegistrationType.FIRSTID.toString())) && getFirstIdAgeValidFlag)) {
			Map<String, String> packetDemographics = priorityBasedPacketManagerService.getFields(registrationId,
					idSchemaUtil.getDefaultFields(Double.valueOf(schemaVersion)), registrationType,
					ProviderStageName.LEGACY_DATA_VALIDATOR);
			Map<String, DocumentDto> packetDocuments = getAllDocumentsByRegId(registrationId, registrationType);
			for (Map.Entry<String, String> entry : packetDemographics.entrySet()) {
				// Check if the value is not null
				if (entry.getValue() != null) {
					demographics.put(entry.getKey(), entry.getValue());
				}
			}
			if (packetDocuments != null) {
				documents.putAll(packetDocuments);
			}
		}
		PacketDto packetDto = new PacketDto();
		packetDto.setId(migrationResponse.getRid());
		packetDto.setSource("DATAMIGRATOR");
		packetDto.setProcess("MIGRATOR");
		packetDto.setRefId(regEntity.getReferenceId());
		packetDto.setSchemaVersion(schemaVersion);
		packetDto.setSchemaJson(idSchemaUtil.getIdSchema(Double.parseDouble(schemaVersion)));
		packetDto.setFields(demographics);
		packetDto.setAudits(auditList);
		packetDto.setMetaInfo(metaInfo);
		packetDto.setDocuments(documents);
		packetDto.setBiometrics(biometrics);
		RequestWrapper<PacketDto> request = new RequestWrapper<>();
		request.setId(ID);
		request.setVersion(VERSION);
		request.setRequesttime(DateUtils.getUTCCurrentDateTime());
		request.setRequest(packetDto);
		ResponseWrapper responseWrapper = (ResponseWrapper<?>) restApi
				.putApi(ApiName.PACKETMANAGER_CREATE_PACKET, null, "", "", request, ResponseWrapper.class,
						null);
		if ((responseWrapper.getErrors() != null && !responseWrapper.getErrors().isEmpty())
				|| responseWrapper.getResponse() == null) {
			ErrorDTO error = (ErrorDTO) responseWrapper.getErrors().get(0);
			regProcLogger.info("Error while creating packet through to packet manager : {} {}", registrationId,
					error.getMessage());
		} else {
			packetManagerService.addOrUpdateTags(migrationResponse.getRid(), tags);
			regProcLogger.info("Successfully created packet through to packet manager : {}", registrationId);
			return packetDto;
		}
		return null;
	}

	private Map<String, String> getBiometricsWSQFormat(String registrationId,
			InternalRegistrationStatusDto registrationStatusDto)
			throws IOException, ApisResourceAccessException, PacketManagerException, JsonProcessingException,
			ValidationFailedException, LegacyDataBiomtericException
	{
		
		JSONObject regProcessorIdentityJson = utility
				.getRegistrationProcessorMappingJson(MappingJsonConstants.IDENTITY);
		String individualBiometricsLabel = JsonUtil.getJSONValue(
				JsonUtil.getJSONObject(regProcessorIdentityJson, MappingJsonConstants.INDIVIDUAL_BIOMETRICS),
				MappingJsonConstants.VALUE);
		List<String> modalities = new ArrayList<>();
		modalities.add("Finger");
		BiometricRecord biometricRecord = priorityBasedPacketManagerService.getBiometrics(registrationId,
				individualBiometricsLabel,
				modalities, registrationStatusDto.getRegistrationType(),
				ProviderStageName.LEGACY_DATA_VALIDATOR);
		if (biometricRecord == null || biometricRecord.getSegments() == null
				|| biometricRecord.getSegments().isEmpty()) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationId, RegistrationStatusCode.FAILED.toString() + "Biometrics are not present for packet");
			throw new LegacyDataBiomtericException(StatusUtil.LEGACY_DATA_BIOMETRIC_FAILED.getMessage(),
					StatusUtil.LEGACY_DATA_BIOMETRIC_FAILED.getCode());
		}
		Map<String, byte[]> isoImageMap = new HashMap<String, byte[]>();
		for (BIR bir : biometricRecord.getSegments()) {
         if(bir.getBdbInfo().getSubtype() != null) {
				String subType = String.join(" ", bir.getBdbInfo().getSubtype());
				String position = Position.getValueFromKey(subType);
				if(bir.getBdb()!=null) {
					isoImageMap.put(position, bir.getBdb());
				}
         }
		}
		
		Map<String, String> wsqFormatBiometrics = convertISOToWSQFormat(isoImageMap);
		regProcLogger.info("Converted ISO to WSQ successfully : {}", registrationId);
		return wsqFormatBiometrics;
	}

	private Map<String, String> convertISOToWSQFormat(Map<String, byte[]> isoImageMap) throws IOException {
		Map<String, String> wsqFormatBiometrics = new HashMap<String, String>();
		for (Map.Entry<String, byte[]> entry : isoImageMap.entrySet()) {
			byte[] wsqData = FingrePrintConvertor.convertIsoToWsq(entry.getValue());
			wsqFormatBiometrics.put(entry.getKey(), CryptoUtil.encodeToPlainBase64(wsqData));
		}
		return wsqFormatBiometrics;
	}

	private boolean checkNINAVailableInLegacy(String registrationId, String NIN, Map<String, String> positionAndWsqMap,
			MessageDTO object)
			throws JAXBException, ApisResourceAccessException, NoSuchAlgorithmException, UnsupportedEncodingException,
			ValidationFailedException, LegacyDataValidationException, JsonProcessingException, JsonMappingException,
			com.fasterxml.jackson.core.JsonProcessingException {
		boolean isValid = false;
		Envelope requestEnvelope = createGetPersonRequest(NIN, positionAndWsqMap);
		String request = marshalToXml(requestEnvelope);
		regProcLogger.debug("(Renewal process)Request to legacy system : {}", request);				
		String response = (String) restApi.postApi(ApiName.LEGACYAPI, "", "", request, String.class,
				MediaType.TEXT_XML);
		regProcLogger.info("Response from legacy system : {}{}", registrationId, response);
		JAXBContext jaxbContext = JAXBContext.newInstance(Envelope.class);
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
		StringReader reader = new StringReader(response);
		Envelope responseEnvelope = (Envelope) unmarshaller.unmarshal(reader);
		VerifyPersonResponse verifyPersonResponse = responseEnvelope.getBody().getVerifyPersonResponse();
		TransactionStatus transactionStatus = verifyPersonResponse.getReturnElement().getTransactionStatus();
		if (transactionStatus.getTransactionStatus().equalsIgnoreCase("Ok")) {
			regProcLogger.info("Matching status for legacy system for NIN present in  : {}{}", registrationId,
					verifyPersonResponse.getReturnElement().isMatchingStatus());
			if (verifyPersonResponse.getReturnElement().isMatchingStatus()) {
				isValid = true;
			}
		} else if (transactionStatus.getTransactionStatus().equalsIgnoreCase("Error")) {
			regProcLogger.info("Transaction status is Error : {}", registrationId);
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationId,
					RegistrationStatusCode.FAILED.toString() + transactionStatus.getError().getCode()
							+ transactionStatus.getError().getMessage());
			regProcLogger.error("Error from  legacy system : {}", registrationId);
			if (transactionStatus.getError().getMessage().contains("Verification of the person is unclear")) {
				MigrationRequestDto migrationRequestDto = new MigrationRequestDto();
				migrationRequestDto.setNin(NIN.toUpperCase());
				RequestWrapper<MigrationRequestDto> requestWrapper = new RequestWrapper();
				requestWrapper.setRequest(migrationRequestDto);
				ResponseWrapper responseWrapper = (ResponseWrapper<?>) restApi.postApi(
						ApiName.MIGARTION_PACKET_CREATION, "", "", requestWrapper, ResponseWrapper.class, null);
				regProcLogger.info("Response from migration api : {}{}", registrationId,
						JsonUtils.javaObjectToJsonString(responseWrapper));
				if (responseWrapper.getErrors() != null && responseWrapper.getErrors().size() > 0) {
					ErrorDTO error = (ErrorDTO) responseWrapper.getErrors().get(0);
					throw new LegacyDataValidationException(transactionStatus.getError().getCode(),
							transactionStatus.getError().getMessage() + "Migration triggered error"
									+ error.getMessage());
				}
				MigrationOnDemandResponse migrationOnDemandResponse = objectMapper.readValue(
						JsonUtils.javaObjectToJsonString(responseWrapper.getResponse()),
						MigrationOnDemandResponse.class);
				if (migrationOnDemandResponse != null) {
					regProcLogger.info(
							"ondemand migration happended  migration rid is  :  {}",
							migrationOnDemandResponse.getRid());
					throw new LegacyDataValidationException(transactionStatus.getError().getCode(),
							transactionStatus.getError().getMessage() + "Migration triggered rid "
									+ migrationOnDemandResponse.getRid());
				} else {
					regProcLogger.info("ondemand migration api response is null  for NIN");
					throw new LegacyDataValidationException(transactionStatus.getError().getCode(),
							transactionStatus.getError().getMessage() + "Migration triggered response null");
				}
			} else {
				throw new LegacyDataValidationException(transactionStatus.getError().getCode(),
						transactionStatus.getError().getMessage());
			}

		}
		return isValid;
	}

	private Envelope createGetPersonRequest(String NIN, Map<String, String> positionAndWsqMap)
			throws NoSuchAlgorithmException, UnsupportedEncodingException {

		byte[] nonceBytes = legacyDataApiUtility.generateNonce();
		String nonce = CryptoUtil.encodeToPlainBase64(nonceBytes);

		String timestamp = legacyDataApiUtility.createTimestamp();
		String timestampForDigest = legacyDataApiUtility.createTimestampForDigest(timestamp);
		String timestampForRequest = timestamp;
		byte[] createdDigestBytes = timestampForDigest.getBytes(StandardCharsets.UTF_8);

		byte[] passwordHashBytes = legacyDataApiUtility.hashPassword();
		String passwordDigest = legacyDataApiUtility.generateDigest(nonceBytes, createdDigestBytes, passwordHashBytes);
		Envelope envelope = new Envelope();
		// Header
		Header header = new Header();
		UsernameToken token = new UsernameToken();
		token.setUsername(username);
		Password password = new Password();
		password.setType("PasswordDigest");
		password.setValue(passwordDigest);
		token.setPassword(password);
		token.setNonce(nonce);
		token.setCreated(timestampForRequest);
		header.setUsernameToken(token);
		envelope.setHeader(header);

		// Body
		Body body = new Body();
		VerifyPerson verifyPerson = new VerifyPerson();
		Request request = new Request();
		request.setNationalId(NIN.toUpperCase());
		List<Fingerprint> fingerprints = new ArrayList<Fingerprint>();
		for (Map.Entry<String, String> entry : positionAndWsqMap.entrySet()) {
			Fingerprint fingerprint = new Fingerprint();
			fingerprint.setPosition(entry.getKey());
			fingerprint.setWsq(entry.getValue());
			fingerprints.add(fingerprint);
		}
		request.setFingerprints(fingerprints);
		verifyPerson.setRequest(request);
		body.setVerifyPerson(verifyPerson);
		envelope.setBody(body);

		return envelope;
	}

	private String marshalToXml(Envelope envelope) throws JAXBException {
		JAXBContext jaxbContext = JAXBContext.newInstance(Envelope.class);
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, false);

		// Use a StringWriter to capture the XML
		java.io.StringWriter sw = new java.io.StringWriter();
		marshaller.marshal(envelope, sw);
		return sw.toString();
	}
	private Map<String, BiometricRecord> getBiometrics(String registrationId, String registrationType)
			throws IOException, ApisResourceAccessException, PacketManagerException, JsonProcessingException
	{
		Map<String, BiometricRecord> biometricData = new HashMap<String, BiometricRecord>();
		JSONObject regProcessorIdentityJson = utility
				.getRegistrationProcessorMappingJson(MappingJsonConstants.IDENTITY);
		String individualBiometricsLabel = JsonUtil.getJSONValue(
				JsonUtil.getJSONObject(regProcessorIdentityJson, MappingJsonConstants.INDIVIDUAL_BIOMETRICS),
				MappingJsonConstants.VALUE);
		BiometricRecord biometricRecord = priorityBasedPacketManagerService.getBiometrics(registrationId, individualBiometricsLabel,
				registrationType,
					ProviderStageName.LEGACY_DATA_VALIDATOR);
			if (biometricRecord != null) {
				biometricData.put(individualBiometricsLabel, biometricRecord);
			}

		return biometricData;
	}

		private void createRegistrationStatusEntity(String stageName, SyncRegistrationEntity regEntity) {
			InternalRegistrationStatusDto dto = registrationStatusService.getRegistrationStatus(
					regEntity.getRegistrationId(), regEntity.getRegistrationType(), 1,
					regEntity.getWorkflowInstanceId());
			if (dto == null) {
				dto = new InternalRegistrationStatusDto();
				dto.setRetryCount(0);
			} else {
				int retryCount = dto.getRetryCount() != null ? dto.getRetryCount() + 1 : 1;
				dto.setRetryCount(retryCount);

			}
			dto.setRegistrationId(regEntity.getRegistrationId());
			dto.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.LEGACY_DATA_VALIDATE.toString());
			dto.setLatestTransactionTimes(DateUtils.getUTCCurrentDateTime());
			dto.setRegistrationStageName(stageName);
			dto.setRegistrationType(regEntity.getRegistrationType());
			dto.setReferenceRegistrationId(null);
			dto.setStatusCode(RegistrationStatusCode.PROCESSING.toString());
			dto.setLangCode("eng");
			dto.setStatusComment(StatusUtil.ON_DEMAND_PACKET_CREATION_SUCCESS.getMessage());
			dto.setSubStatusCode(StatusUtil.ON_DEMAND_PACKET_CREATION_SUCCESS.getCode());
			dto.setReProcessRetryCount(0);
			dto.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.SUCCESS.toString());
			dto.setIsActive(true);
			dto.setCreatedBy("MOSIP");
			dto.setIsDeleted(false);
			dto.setSource(regEntity.getSource());
			dto.setIteration(1);
			dto.setWorkflowInstanceId(regEntity.getWorkflowInstanceId());

			/** Module-Id can be Both Success/Error code */
			String moduleId = PlatformSuccessMessages.RPR_LEGACY_DATA_VALIDATE.getCode();
			String moduleName = ModuleName.LEGACY_DATA_VALIDATE.toString();
			registrationStatusService.addRegistrationStatus(dto, moduleId, moduleName);
			regProcLogger.info("Successfully created record in registration for ondemand packet : {} ",
					regEntity.getRegistrationId());
		}

		private Map<String, DocumentDto> getAllDocumentsByRegId(String regId, String process)
				throws ApisResourceAccessException, PacketManagerException, JsonProcessingException, IOException {
				
			JSONObject docJson = utility.getRegistrationProcessorMappingJson(MappingJsonConstants.DOCUMENT);
			Map<String, DocumentDto> documents = new HashMap<String, DocumentDto>();
			for (Object doc : docJson.values()) {
				Map docMap = (LinkedHashMap) doc;
				String docValue = docMap.values().iterator().next().toString();
					DocumentDto documentDto = getIdDocument(regId, docValue, process);
					if (documentDto != null) {
						documents.put(docValue, documentDto);
					}

			}
			return documents;
		}

		private DocumentDto getIdDocument(String registrationId, String dockey, String process)
				throws IOException, ApisResourceAccessException, PacketManagerException,
				io.mosip.kernel.core.util.exception.JsonProcessingException {
			Document document = priorityBasedPacketManagerService.getDocument(registrationId, dockey, process,
					ProviderStageName.LEGACY_DATA_VALIDATOR);
			if (document != null) {
				DocumentDto documentDto = new DocumentDto();
				documentDto.setDocument(document.getDocument());
				documentDto.setFormat(document.getFormat());
				documentDto.setType(document.getFormat());
				documentDto.setValue(document.getValue());
				return documentDto;
			}
			return null;
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
}
