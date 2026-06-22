
package io.mosip.registration.processor.credentialrequestor.stage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.exception.ServiceError;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.core.websub.model.Event;
import io.mosip.kernel.core.websub.model.EventModel;
import io.mosip.registration.processor.core.abstractverticle.*;
import io.mosip.registration.processor.core.code.EventId;
import io.mosip.registration.processor.core.code.EventName;
import io.mosip.registration.processor.core.code.EventType;
import io.mosip.registration.processor.core.code.*;
import io.mosip.registration.processor.core.common.rest.dto.ErrorDTO;
import io.mosip.registration.processor.core.constant.*;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.RegistrationProcessorCheckedException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.exception.util.PlatformSuccessMessages;
import io.mosip.registration.processor.core.http.RequestWrapper;
import io.mosip.registration.processor.core.http.ResponseWrapper;
import io.mosip.registration.processor.core.idrepo.dto.CredentialRequestDto;
import io.mosip.registration.processor.core.idrepo.dto.CredentialResponseDto;
import io.mosip.registration.processor.core.idrepo.dto.VidInfoDTO;
import io.mosip.registration.processor.core.idrepo.dto.VidsInfosDTO;
import io.mosip.registration.processor.core.logger.LogDescription;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.core.status.util.StatusUtil;
import io.mosip.registration.processor.core.status.util.TrimExceptionMessage;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.credentialrequestor.dto.CredentialPartner;
import io.mosip.registration.processor.credentialrequestor.stage.exception.VidNotAvailableException;
import io.mosip.registration.processor.credentialrequestor.util.CredentialPartnerUtil;
import io.mosip.registration.processor.credentialrequestor.util.WebSubUtil;
import io.mosip.registration.processor.packet.storage.entity.MAMatchedRidsEntity;
import io.mosip.registration.processor.packet.storage.repository.BasePacketRepository;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.rest.client.audit.builder.AuditLogRequestBuilder;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.entity.NotificationMessageEntity;
import io.mosip.registration.processor.status.service.NotificationMessageService;
import io.mosip.registration.processor.status.service.RegistrationStatusService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The Class PrintStage.
 * 
 * @author M1048358 Alok
 * @author Ranjitha Siddegowda
 * @author Sowmya
 */
@RefreshScope
@Service
@Configuration
@EnableScheduling
@ComponentScan(basePackages = { "${mosip.auth.adapter.impl.basepackage}",
		"io.mosip.registration.processor.core.config",
		"io.mosip.registration.processor.stages.config", 
		"io.mosip.registration.processor.credentialrequestor.config",
		"io.mosip.registrationprocessor.stages.config",
		"io.mosip.registration.processor.status.config",
		"io.mosip.registration.processor.rest.client.config", 
		"io.mosip.registration.processor.packet.storage.config",
		"io.mosip.registration.processor.packet.manager.config", 
		"io.mosip.kernel.idobjectvalidator.config",
		"io.mosip.registration.processor.core.kernel.beans",
		"io.mosip.kernel.websub.api.client",
		"io.mosip.kernel.websub.api.config.publisher"})
public class CredentialRequestorStage extends MosipVerticleAPIManager {
	
	private static final String STAGE_PROPERTY_PREFIX = "mosip.regproc.credentialrequestor.";
	private Random sr = null;
	private static final int max = 999999;
	private static final int min = 100000;

	/** The reg proc logger. */
	private static Logger regProcLogger = RegProcessorLogger.getLogger(CredentialRequestorStage.class);

	/** The cluster manager url. */
	@Value("${vertx.cluster.configuration}")
	private String clusterManagerUrl;


	/** The core audit request builder. */
	@Autowired
	private AuditLogRequestBuilder auditLogRequestBuilder;

	/** The mosip event bus. */
	private MosipEventBus mosipEventBus;

	/** The registration status service. */
	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;
	
	@Autowired
	private BasePacketRepository<MAMatchedRidsEntity, String> matchedRidsRepository;

	/** worker pool size. */
	@Value("${worker.pool.size}")
	private Integer workerPoolSize;

	/** After this time intervel, message should be considered as expired (In seconds). */
	@Value("${mosip.regproc.credentialrequestor.message.expiry-time-limit}")
	private Long messageExpiryTimeLimit;

	@Value("${mosip.registration.processor.encrypt:false}")
	private boolean encrypt;
	
	@Value("${mosip.opencrvs.failed.scheduler.fetchsize:5}")
	private Integer failedFetchSize;
	@Value("${mosip.opencrvs.credential.scheduler.fetchsize:5}")
	private Integer fetchSize;

	/** Mosip router for APIs */
	@Autowired
	MosipRouter router;

	private static final String SEPERATOR = "::";

	@Autowired
	private RegistrationProcessorRestClientService<Object> restClientService;

	@Autowired
	private ObjectMapper mapper;

	@Autowired
	private Environment env;

	private static final String DATETIME_PATTERN = "mosip.registration.processor.datetime.pattern";

	private static final String ISSUERS = "mosip.registration.processor.issuer";

	@Value("#{T(java.util.Arrays).asList('${mosip.registration.processor.credential.default.partner-ids:}')}")
	private List<String> defaultPartners;

	private static String COMMA = ",";
	private static String HASH_DELIMITER = "#";

	@Autowired
	private Utilities utilities;

	@Autowired
	private CredentialPartnerUtil credentialPartnerUtil;
	
	@Autowired
	private NotificationMessageService notificationMessageService;
	
	@Autowired
	private WebSubUtil webSubUtil;

	@Override
	protected String getPropertyPrefix() {
		return STAGE_PROPERTY_PREFIX;
	}

	/**
	 * Deploy verticle.
	 */
	public void deployVerticle() {
		mosipEventBus = this.getEventBus(this, clusterManagerUrl, workerPoolSize);
		this.consumeAndSend(mosipEventBus, MessageBusAddress.PRINTING_BUS_IN, MessageBusAddress.PRINTING_BUS_OUT,
				messageExpiryTimeLimit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * io.mosip.registration.processor.core.spi.eventbus.EventBusManager#process(
	 * java.lang.Object)
	 */
	@Override
	public MessageDTO process(MessageDTO object) {
		TrimExceptionMessage trimeExpMessage = new TrimExceptionMessage();
		object.setMessageBusAddress(MessageBusAddress.PRINTING_BUS_IN);
		object.setInternalError(Boolean.FALSE);
		object.setIsValid(Boolean.FALSE);
		LogDescription description = new LogDescription();

		boolean isTransactionSuccessful = false;
		String uin = null;
		String refIds = null;
		boolean updateTransaction = true;
		String regId = object.getRid();
		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
				regId, "PrintStage::process()::entry");

		InternalRegistrationStatusDto registrationStatusDto = null;
		RequestWrapper<CredentialRequestDto> requestWrapper = new RequestWrapper<>();
		ResponseWrapper<?> responseWrapper = null;
		CredentialResponseDto credentialResponseDto;
		try {
			registrationStatusDto = registrationStatusService.getRegistrationStatus(
					regId, object.getReg_type(), object.getIteration(), object.getWorkflowInstanceId());
			registrationStatusDto
					.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PRINT_SERVICE.toString());
			registrationStatusDto.setRegistrationStageName(getStageName());
			String registrationType = registrationStatusDto.getRegistrationType();
			JSONObject jsonObject = utilities.idrepoRetrieveIdentityByRid(regId);
			uin = JsonUtil.getJSONValue(jsonObject, IdType.UIN.toString());
			if (uin == null) {
				regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), null,
						PlatformErrorMessages.RPR_PRT_UIN_NOT_FOUND_IN_DATABASE.name());
				object.setIsValid(Boolean.FALSE);
				isTransactionSuccessful = false;
				description.setMessage(PlatformErrorMessages.RPR_PRT_PRINT_REQUEST_FAILED.getMessage());
				description.setCode(PlatformErrorMessages.RPR_PRT_PRINT_REQUEST_FAILED.getCode());

				registrationStatusDto.setStatusComment(
						StatusUtil.UIN_NOT_FOUND_IN_DATABASE.getMessage());
				registrationStatusDto.setSubStatusCode(StatusUtil.UIN_NOT_FOUND_IN_DATABASE.getCode());
				registrationStatusDto
						.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.FAILED.toString());
				registrationStatusDto
						.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PRINT_SERVICE.toString());

			} else {
				requestWrapper.setId(env.getProperty("mosip.registration.processor.credential.request.service.id"));
				DateTimeFormatter format = DateTimeFormatter.ofPattern(env.getProperty(DATETIME_PATTERN));
				requestWrapper.setVersion("1.0");
				List<CredentialPartner> allIssuerList = credentialPartnerUtil.getAllCredentialPartners().getPartners();
				// filtering with default partner ids and process
				List<CredentialPartner> filteredPartners = allIssuerList.stream()
						.filter(issuer -> defaultPartners.contains(issuer.getId()))
						.filter(issuer -> (issuer.getProcess() == null) || (issuer.getProcess().contains(object.getReg_type())))
						.collect(Collectors.toList());
				filteredPartners.addAll(credentialPartnerUtil.getCredentialPartners(
						regId, registrationStatusDto.getRegistrationType(), jsonObject));
				
				boolean isCrvsFlow = (regId != null && regId.contains("-")) || "CRVS_NEW".equals(object.getReg_type());

				if (isCrvsFlow) {
				    allIssuerList.stream()
				            .filter(p -> "opencrvsPartner".equals(p.getId()))
				            .findFirst()
				            .ifPresent(opencrvsPartner -> {
				                boolean alreadyPresent = filteredPartners.stream()
				                        .anyMatch(p -> "opencrvsPartner".equals(p.getId()));
				                if (!alreadyPresent) {
				                    filteredPartners.add(opencrvsPartner);
				                }
				            });
				} else {
				    filteredPartners.removeIf(p -> "opencrvsPartner".equals(p.getId()));
				}
				
				boolean isAdult = object.getTags() != null && "ADULT".equals(object.getTags().get("AGE_GROUP"));
				if (!RegistrationType.NEW.toString().equalsIgnoreCase(registrationType)) {
					isAdult = true;
				}

				Map<String, String> tags = object.getTags();
				String userServiceType = null;
				if (tags != null) {
					userServiceType = tags.getOrDefault("ID_OBJECT-applicantCitizenshipType",
							tags.get("ID_OBJECT-userServiceType"));
				}

				boolean isAlien = "Alien New Registration".equals(userServiceType);

				if (!isAdult && !isAlien) {
					filteredPartners.removeIf(p -> "printPartner".equals(p.getId()));
				}
				
				for (CredentialPartner key : filteredPartners) {
					CredentialRequestDto credentialRequestDto = getCredentialRequestDto(regId, registrationStatusDto.getRegistrationType(), key);
					LocalDateTime localdatetime = LocalDateTime.parse(
							DateUtils.getUTCCurrentDateTimeString(env.getProperty(DATETIME_PATTERN)), format);
					requestWrapper.setRequesttime(localdatetime);
					requestWrapper.setRequest(credentialRequestDto);
					// issuers with appIdBasedCredentialIdSuffix is calling v1 api and for others stage is calling v2 api for credential
					if (StringUtils.isNotEmpty(key.getAppIdBasedCredentialIdSuffix())) {
						List<String> pathsegments = new ArrayList<>();
						pathsegments.add(regId + key.getAppIdBasedCredentialIdSuffix()); //  #PDF suffix is added to identify the requested credential via rid
						responseWrapper = (ResponseWrapper<?>) restClientService.postApi(ApiName.CREDENTIALREQUESTV2, MediaType.APPLICATION_JSON, pathsegments, null,
									null, requestWrapper, ResponseWrapper.class);
					} else {
						responseWrapper = (ResponseWrapper<?>) restClientService.postApi(ApiName.CREDENTIALREQUEST, null, null,
								requestWrapper, ResponseWrapper.class, MediaType.APPLICATION_JSON);
					}
					if (responseWrapper.getErrors() != null && !responseWrapper.getErrors().isEmpty()) {
						ErrorDTO error = responseWrapper.getErrors().get(0);
						object.setIsValid(Boolean.FALSE);
						isTransactionSuccessful = false;
						registrationStatusDto.setRefId(refIds);
						description.setMessage(PlatformErrorMessages.RPR_PRT_PRINT_REQUEST_FAILED.getMessage());
						description.setCode(PlatformErrorMessages.RPR_PRT_PRINT_REQUEST_FAILED.getCode());

						registrationStatusDto.setStatusComment(
								StatusUtil.PRINT_REQUEST_FAILED.getMessage() + SEPERATOR + error.getMessage());
						registrationStatusDto.setSubStatusCode(StatusUtil.PRINT_REQUEST_FAILED.getCode());
						registrationStatusDto
								.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.REPROCESS.toString());
						registrationStatusDto
								.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PRINT_SERVICE.toString());
						break;
					} else {
						credentialResponseDto = mapper.readValue(mapper.writeValueAsString(responseWrapper.getResponse()),
								CredentialResponseDto.class);
						refIds = credentialResponseDto.getRequestId();
						isTransactionSuccessful = true;
					}
				}
				
				if (filteredPartners.size() == 0) {
					updateTransaction = false;
					object.setIsValid(Boolean.TRUE);
				}
				
				if (isTransactionSuccessful) {
					registrationStatusDto.setRefId(refIds);
					object.setIsValid(Boolean.TRUE);
					description.setMessage(PlatformSuccessMessages.RPR_PRINT_STAGE_REQUEST_SUCCESS.getMessage());
					description.setCode(PlatformSuccessMessages.RPR_PRINT_STAGE_REQUEST_SUCCESS.getCode());
					registrationStatusDto.setStatusComment(
							trimeExpMessage.trimExceptionMessage(StatusUtil.PRINT_REQUEST_SUCCESS.getMessage()));
					registrationStatusDto.setSubStatusCode(StatusUtil.PRINT_REQUEST_SUCCESS.getCode());
					registrationStatusDto
							.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.PROCESSED.toString());
					registrationStatusDto
							.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PRINT_SERVICE.toString());

					regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString(), regId, "PrintStage::process()::exit");
				}
			}
		} catch (ApisResourceAccessException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					regId, PlatformErrorMessages.RPR_PRT_PRINT_REQUEST_FAILED.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			registrationStatusDto
					.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.REPROCESS.toString());
			registrationStatusDto.setStatusComment(trimeExpMessage.trimExceptionMessage(
					StatusUtil.API_RESOUCE_ACCESS_FAILED.getMessage() + SEPERATOR + e.getMessage()));
			registrationStatusDto.setSubStatusCode(StatusUtil.API_RESOUCE_ACCESS_FAILED.getCode());
			description.setMessage(PlatformErrorMessages.RPR_PRT_PRINT_REQUEST_FAILED.getMessage());
			description.setCode(PlatformErrorMessages.RPR_PRT_PRINT_REQUEST_FAILED.getCode());
			object.setInternalError(Boolean.TRUE);
		} catch (IOException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					regId, PlatformErrorMessages.RPR_PRT_PRINT_REQUEST_FAILED.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			registrationStatusDto
					.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.REPROCESS.toString());
			registrationStatusDto.setStatusComment(
					trimeExpMessage.trimExceptionMessage(StatusUtil.IO_EXCEPTION.getMessage() + e.getMessage()));
			registrationStatusDto.setSubStatusCode(StatusUtil.IO_EXCEPTION.getCode());
			description.setMessage(PlatformErrorMessages.RPR_PRT_PRINT_REQUEST_FAILED.getMessage());
			description.setCode(PlatformErrorMessages.RPR_PRT_PRINT_REQUEST_FAILED.getCode());
			object.setInternalError(Boolean.TRUE);
		} catch (Exception e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					regId, PlatformErrorMessages.RPR_PRT_PRINT_REQUEST_FAILED.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			registrationStatusDto
					.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.REPROCESS.toString());
			registrationStatusDto.setStatusComment(
					trimeExpMessage.trimExceptionMessage(StatusUtil.UNKNOWN_EXCEPTION_OCCURED.getMessage()));
			registrationStatusDto.setSubStatusCode(StatusUtil.UNKNOWN_EXCEPTION_OCCURED.getCode());
			description.setMessage(PlatformErrorMessages.RPR_PRT_PRINT_REQUEST_FAILED.getMessage());
			description.setCode(PlatformErrorMessages.RPR_PRT_PRINT_REQUEST_FAILED.getCode());
			object.setInternalError(Boolean.TRUE);
		}
		finally {
			if (object.getInternalError()) {
				updateErrorFlags(registrationStatusDto, object);
			}
			String eventId = "";
			String eventName = "";
			String eventType = "";
			eventId = isTransactionSuccessful ? EventId.RPR_402.toString() : EventId.RPR_405.toString();
			eventName = eventId.equalsIgnoreCase(EventId.RPR_402.toString()) ? EventName.UPDATE.toString()
					: EventName.EXCEPTION.toString();
			eventType = eventId.equalsIgnoreCase(EventId.RPR_402.toString()) ? EventType.BUSINESS.toString()
					: EventType.SYSTEM.toString();
			/** Module-Id can be Both Success/Error code */
			String moduleId = isTransactionSuccessful
					? PlatformSuccessMessages.RPR_PRINT_STAGE_REQUEST_SUCCESS.getCode()
					: description.getCode();
			String moduleName = ModuleName.PRINT_STAGE.toString();
			
			if(updateTransaction) {
				registrationStatusService.updateRegistrationStatus(registrationStatusDto, moduleId, moduleName);
			}

			auditLogRequestBuilder.createAuditRequestBuilder(description.getMessage(), eventId, eventName, eventType,
					moduleId, moduleName, regId);

		}
		return object;
	}
	
	@Scheduled(cron = "${mosip.opencrvs.credential.cron.expression:0 0/3 * * * ?}")
	public void issueOpenCrvsCredential() {
		regProcLogger.info("Batch job for opencrvs credentials started");
		try {
			List<CredentialPartner> allIssuerList = credentialPartnerUtil.getAllCredentialPartners().getPartners();
			Optional<CredentialPartner> issuerOpt = allIssuerList.stream().filter(issuer -> "opencrvsPartner".equals(issuer.getId())).findFirst();
			
			if (issuerOpt.isPresent()) {
				List<MAMatchedRidsEntity> records =
				        matchedRidsRepository.findPendingForIssue(fetchSize);
				
				records.forEach(record -> {
					issueCredentialToOpenCrvs(record, issuerOpt.get());
				});
			} else {
				regProcLogger.error("Issuer not found");
			}
		} catch (RegistrationProcessorCheckedException e) {
			regProcLogger.error("Batch job failed, unable to get the issuer");
		}
		
		regProcLogger.info("Batch job completed");
	}
	
	private void issueCredentialToOpenCrvs(MAMatchedRidsEntity record, CredentialPartner key) {
		try {
			String regId = record.getId().getRegId();
			String matchedRegId = record.getMatchedRegIds();
			CredentialRequestDto credentialRequestDto = new CredentialRequestDto();
			Map<String, Object> additionalAttributes=new HashMap<>();

			credentialRequestDto.setCredentialType(key.getCredentialType());
			credentialRequestDto.setEncrypt(encrypt);

			credentialRequestDto.setId(matchedRegId);

			credentialRequestDto.setIssuer(key.getPartnerId());

			credentialRequestDto.setEncryptionKey(generatePin());
			additionalAttributes.put("templateTypeCode", key.getTemplate());
			additionalAttributes.put("registrationId", regId);
			credentialRequestDto.setAdditionalData(additionalAttributes);
			
			RequestWrapper<CredentialRequestDto> requestWrapper = new RequestWrapper<>();
			requestWrapper.setId(env.getProperty("mosip.registration.processor.credential.request.service.id"));
			DateTimeFormatter format = DateTimeFormatter.ofPattern(env.getProperty(DATETIME_PATTERN));
			requestWrapper.setVersion("1.0");
			LocalDateTime localdatetime = LocalDateTime.parse(
					DateUtils.getUTCCurrentDateTimeString(env.getProperty(DATETIME_PATTERN)), format);
			requestWrapper.setRequesttime(localdatetime);
			requestWrapper.setRequest(credentialRequestDto);
			
			ResponseWrapper<?> responseWrapper = null;
			// issuers with appIdBasedCredentialIdSuffix is calling v1 api and for others stage is calling v2 api for credential
			if (StringUtils.isNotEmpty(key.getAppIdBasedCredentialIdSuffix())) {
				List<String> pathsegments = new ArrayList<>();
				pathsegments.add(regId + key.getAppIdBasedCredentialIdSuffix()); //  #PDF suffix is added to identify the requested credential via rid
				responseWrapper = (ResponseWrapper<?>) restClientService.postApi(ApiName.CREDENTIALREQUESTV2, MediaType.APPLICATION_JSON, pathsegments, null,
							null, requestWrapper, ResponseWrapper.class);
			} else {
				responseWrapper = (ResponseWrapper<?>) restClientService.postApi(ApiName.CREDENTIALREQUEST, null, null,
						requestWrapper, ResponseWrapper.class, MediaType.APPLICATION_JSON);
			}
			
			if (responseWrapper.getErrors() != null && !responseWrapper.getErrors().isEmpty()) {
				ErrorDTO error = responseWrapper.getErrors().get(0);
				record.setRemark(error.getMessage());
			} else {
				CredentialResponseDto credentialResponseDto = mapper.readValue(mapper.writeValueAsString(responseWrapper.getResponse()),
						CredentialResponseDto.class);
				record.setCredentialId(credentialResponseDto.getRequestId());
				record.setIssued(true);
				record.setRemark(null);
			}
		} catch (Exception e) {
			e.printStackTrace();
			regProcLogger.error("Failed to issue the credential");
			record.setRemark(e.getMessage());
		}
		
		record.setUpdBy("SYSTEM");
		record.setUpdDtimes(Timestamp.valueOf(LocalDateTime.now(ZoneId.of("UTC"))));
		matchedRidsRepository.save(record);
	}

	@Scheduled(cron = "${mosip.opencrvs.failed.records.cron.expression:0 0/3 * * * ?}")
	public void sendOpenCrvsFailedRecords() {
		regProcLogger.info("Batch job for opencrvs failed records started");

		List<NotificationMessageEntity> records = notificationMessageService.getRecordsNotSentToOpencrvs(failedFetchSize);

		regProcLogger.info("opencrvs failed records picked: ", records.size());
		
		records.forEach(record -> {
			sendFailedRecordToOpencrvs(record);
		});

		regProcLogger.info("Batch job completed");
	}
	
	private void sendFailedRecordToOpencrvs(NotificationMessageEntity record) {
		EventModel eventModel = new EventModel();
		DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		LocalDateTime localdatetime = LocalDateTime
				.parse(DateUtils.getUTCCurrentDateTimeString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"), format);
		eventModel.setPublishedOn(DateUtils.toISOString(localdatetime));
		eventModel.setPublisher("CREDENTIAL_REQUEST_STAGE");
		eventModel.setTopic("OPENCRVS_ERROR");
		
		Event event = new Event();
		event.setId(UUID.randomUUID().toString());

		Map<String, Object> map = new HashMap<>();
		map.put("registrationId", record.getRegId());

		String failureReason = null;
        try {
            Map<String, String> messageMap = mapper.readValue(record.getNotificationMessage(), new TypeReference<Map<String, String>>() {});
			failureReason = messageMap.get("FAILURE_REASON");
			if(failureReason == null) {
				failureReason = messageMap.get("REJECTION_COMMENT");
			}
        } catch (JsonProcessingException ignored) {

        }
        map.put("failureReason", failureReason);
		event.setData(map);
		event.setTimestamp(DateUtils.toISOString(localdatetime));
		
		eventModel.setEvent(event);
		
		webSubUtil.publishSuccess("OPENCRVS_ERROR", eventModel);
		
		record.setUpdatedBy("SYSTEM");
		record.setUpdateDateTime(LocalDateTime.now(ZoneId.of("UTC")));
		record.setSentToOpencrvs(true);
		
		notificationMessageService.saveRecord(record);
	}

	private CredentialRequestDto getCredentialRequestDto(String regId, String process, CredentialPartner key) {
		CredentialRequestDto credentialRequestDto = new CredentialRequestDto();
		Map<String, Object> additionalAttributes=new HashMap<>();

		credentialRequestDto.setCredentialType(key.getCredentialType());
		credentialRequestDto.setEncrypt(encrypt);

		credentialRequestDto.setId(regId);

		credentialRequestDto.setIssuer(key.getPartnerId());

		credentialRequestDto.setEncryptionKey(generatePin());
		additionalAttributes.put("templateTypeCode", key.getTemplate());
		additionalAttributes.put("registrationId", regId);
		if (CollectionUtils.isNotEmpty(key.getMetaInfoFields()))
			getAdditionalCredentialFields(regId, process, key.getMetaInfoFields(), additionalAttributes);
		credentialRequestDto.setAdditionalData(additionalAttributes);

		return credentialRequestDto;
	}

	private void getAdditionalCredentialFields(String regId, String process,
											   List<String> metaInfoFields,
											   Map<String, Object> additionalAttributes) {
		try {
			Map<String,String> metaInfo = utilities.getPacketManagerService().getMetaInfo(regId, process, ProviderStageName.CREDENTIAL_REQUESTOR);
			JSONArray metadata = new JSONArray(metaInfo.get(JsonConstant.METADATA));
			for(int i=0; i<metadata.length(); i++){
				org.json.JSONObject jsonObject = metadata.getJSONObject(i);
				if (metaInfoFields.contains(jsonObject.getString("label"))){
					additionalAttributes.put(jsonObject.getString("label"), jsonObject.getString("value"));
				}
			}

		} catch (Exception e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					regId, RegistrationStatusCode.FAILED + e.getMessage() + ExceptionUtils.getStackTrace(e));
			throw new BaseUncheckedException(PlatformErrorMessages.RPR_PRT_PARSING_ADDITIONAL_CRED_CONFIG.getCode(),
					PlatformErrorMessages.RPR_PRT_PARSING_ADDITIONAL_CRED_CONFIG.getMessage(), e);
		}
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see io.vertx.core.AbstractVerticle#start()
	 */
	@Override
	public void start() {
		router.setRoute(this.postUrl(getVertx(), 
				MessageBusAddress.PRINTING_BUS_IN, MessageBusAddress.PRINTING_BUS_OUT));
		this.createServer(router.getRouter(), getPort());
	}

	public String generatePin() {
		if (sr == null)
			instantiate();
		int randomInteger = sr.nextInt(max - min) + min;
		return String.valueOf(randomInteger);
	}

	@SuppressWarnings("unchecked")
	private String getVid(String uin) throws ApisResourceAccessException, VidNotAvailableException {
		List<String> pathsegments = new ArrayList<>();
		pathsegments.add(uin);
		String vid = null;

		VidsInfosDTO vidsInfosDTO;

		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
				"PrintServiceImpl::getVid():: get GETVIDSBYUIN service call started with request data : "
		);

		vidsInfosDTO =  (VidsInfosDTO) restClientService.getApi(ApiName.GETVIDSBYUIN,
				pathsegments, "", "", VidsInfosDTO.class);
	
		if (vidsInfosDTO.getErrors() != null && !vidsInfosDTO.getErrors().isEmpty()) {
			ServiceError error = vidsInfosDTO.getErrors().get(0);
			throw new VidNotAvailableException(PlatformErrorMessages.RPR_PRT_VID_NOT_AVAILABLE_EXCEPTION.getCode(),
					error.getMessage());

		} else {
			if(vidsInfosDTO.getResponse()!=null && !vidsInfosDTO.getResponse().isEmpty()) {
				for (VidInfoDTO VidInfoDTO : vidsInfosDTO.getResponse()) {
					if (VidType.PERPETUAL.name().equalsIgnoreCase(VidInfoDTO.getVidType())) {
						vid = VidInfoDTO.getVid();
						break;
					}
				}
				if (vid == null) {
					throw new VidNotAvailableException(
							PlatformErrorMessages.RPR_PRT_VID_NOT_AVAILABLE_EXCEPTION.getCode(),
							PlatformErrorMessages.RPR_PRT_VID_NOT_AVAILABLE_EXCEPTION.getMessage());
				}
				regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), "",
						"PrintServiceImpl::getVid():: get GETVIDSBYUIN service call ended successfully");

			}else {
				throw new VidNotAvailableException(PlatformErrorMessages.RPR_PRT_VID_NOT_AVAILABLE_EXCEPTION.getCode(),
						PlatformErrorMessages.RPR_PRT_VID_NOT_AVAILABLE_EXCEPTION.getMessage());
			}
			
		}

		return vid;
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

	@Scheduled(fixedDelayString = "${mosip.regproc.printstage.pingeneration.refresh.millisecs:1800000}",
			initialDelayString = "${mosip.regproc.printstage.pingeneration.refresh.delay-on-startup.millisecs:5000}")
	private void instantiate() {
		regProcLogger.debug("Instantiating SecureRandom for credential pin generation............");
		try {
			sr = SecureRandom.getInstance("SHA1PRNG");
		} catch (NoSuchAlgorithmException e) {
			regProcLogger.error("Could not instantiate SecureRandom for credential pin generation", e);
		}
	}
}
