package io.mosip.registration.processor.stages.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.code.ApiName;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.exception.TemplateProcessingFailureException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.exception.util.PlatformSuccessMessages;
import io.mosip.registration.processor.core.http.RequestWrapper;
import io.mosip.registration.processor.core.http.ResponseWrapper;
import io.mosip.registration.processor.core.logger.LogDescription;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.notification.template.generator.dto.ResponseDto;
import io.mosip.registration.processor.core.notification.template.generator.dto.SmsRequestDto;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.core.status.util.StatusUtil;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.core.util.LanguageUtility;
import io.mosip.registration.processor.message.sender.exception.TemplateGenerationFailedException;
import io.mosip.registration.processor.message.sender.exception.TemplateNotFoundException;
import io.mosip.registration.processor.message.sender.template.TemplateGenerator;
import io.mosip.registration.processor.packet.storage.utils.PriorityBasedPacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.rest.client.utils.RestApiClient;
import io.mosip.registration.processor.stages.dto.AsyncRequestDTO;
import io.mosip.registration.processor.stages.dto.MessageSenderDTO;
import io.mosip.registration.processor.stages.helper.RestHelper;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationAdditionalInfoDTO;
import io.mosip.registration.processor.status.dto.SyncTypeDto;
import io.mosip.registration.processor.status.entity.SyncRegistrationEntity;
@Component
public class NotificationUtility {

	/** The reg proc logger. */
	private static Logger regProcLogger = RegProcessorLogger.getLogger(NotificationUtility.class);

	/** The Constant BOTH. */
	public static final String BOTH = "BOTH";

	/** The Constant LINE_SEPARATOR. */
	public static final String LINE_SEPARATOR = "" + '\n' + '\n' + '\n';

	/** The Constant FILE_SEPARATOR. */
	public static final String FILE_SEPARATOR = File.separator;

	/** The Constant ENCODING. */
	public static final String ENCODING = "UTF-8";

	@Autowired
	private RegistrationProcessorRestClientService<Object> restClientService;


	/** The primary language. */
	@Value("${mosip.default.template-languages:#{null}}")
	private String defaultTemplateLanguages;

	@Value("${mosip.notification.language-type}")
	private String languageType;

	@Value("${mosip.default.user-preferred-language-attribute:#{null}}")
	private String userPreferredLanguageAttribute;

	@Value("${registration.processor.notification.service.email.enable.for.other.process:true}")
	private boolean enableEmailForOtherProcess;

	@Value("${registration.processor.notification.timezone}")
	private String timeZone;

	/** The env. */
	@Autowired
	private Environment env;

	/** The template generator. */
	@Autowired
	private TemplateGenerator templateGenerator;
	
	@Autowired
	private LanguageUtility languageUtility;

	/** The resclient. */
	@Autowired
	private RestApiClient resclient;
	
	@Autowired
	private PriorityBasedPacketManagerService packetManagerService;
	
	/** The utility. */
	@Autowired
	private Utilities utility;

	@Autowired
	private RestHelper restHelper;

	private static final String SMS_SERVICE_ID = "mosip.registration.processor.sms.id";
	private static final String REG_PROC_APPLICATION_VERSION = "mosip.registration.processor.application.version";
	private static final String DATETIME_PATTERN = "mosip.registration.processor.datetime.pattern";
	private static final String NOTIFICATION_TEMPLATE_CODE="regproc.packet.validator.notification.template.code.";
	private static final String EMAIL="email";
	private static final String SMS="sms";
	private static final String SUB="sub";
	private static final String NEW_REG=NOTIFICATION_TEMPLATE_CODE+"new.reg.";
	private static final String LOST_UIN=NOTIFICATION_TEMPLATE_CODE+"lost.uin.";
	private static final String REPRINT_UIN=NOTIFICATION_TEMPLATE_CODE+"reprint.uin.";
	private static final String ACTIVATE=NOTIFICATION_TEMPLATE_CODE+"activate.";
	private static final String DEACTIVATE=NOTIFICATION_TEMPLATE_CODE+"deactivate.";
	private static final String UIN_UPDATE=NOTIFICATION_TEMPLATE_CODE+"uin.update.";
	private static final String UIN_RENEWAL=NOTIFICATION_TEMPLATE_CODE+"uin.renewal.";
	private static final String RES_UPDATE=NOTIFICATION_TEMPLATE_CODE+"resident.update.";
	private static final String TECHNICAL_ISSUE=NOTIFICATION_TEMPLATE_CODE+"technical.issue.";
	private static final String SUP_REJECT=NOTIFICATION_TEMPLATE_CODE+"supervisor.reject.";
	private static final String GET_FIRSTID = NOTIFICATION_TEMPLATE_CODE + "get.firstid.";



	@Autowired
	private ObjectMapper mapper;

	public void sendNotification(RegistrationAdditionalInfoDTO registrationAdditionalInfoDTO,
			InternalRegistrationStatusDto registrationStatusDto, SyncRegistrationEntity regEntity,
			String[] allNotificationTypes, boolean isProcessingSuccess,boolean isValidSupervisorStatus)
			throws ApisResourceAccessException, IOException, PacketManagerException, JsonProcessingException, JSONException {
		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
				"", "NotificationUtility::sendNotification()::entry");

		String registrationId = regEntity.getRegistrationId();
		LogDescription description = new LogDescription();
		String regType = regEntity.getRegistrationType();
		MessageSenderDTO messageSenderDTO = new MessageSenderDTO();
		NotificationTemplateType type = null;
		Map<String, Object> attributes = new HashMap<>();
		attributes.put("RID", registrationId);
		List<String> preferredLanguages=getPreferredLanguages(registrationStatusDto);
		JSONObject regProcessorIdentityJson = utility.getRegistrationProcessorMappingJson(MappingJsonConstants.IDENTITY);
        String nameField = JsonUtil.getJSONValue(
                JsonUtil.getJSONObject(regProcessorIdentityJson, MappingJsonConstants.NAME),
                MappingJsonConstants.VALUE);
		String[] nameArray = nameField.toString().split(",");
		String jsonServiceTypeObj =  packetManagerService.getField(regEntity.getRegistrationId(), MappingJsonConstants.SERVICE_TYPE, regEntity.getRegistrationType(), ProviderStageName.PACKET_VALIDATOR);
		
		String userServiceType = null;
		if (jsonServiceTypeObj != null) {
			JSONArray userServiceTypeArray = new JSONArray(jsonServiceTypeObj);
			userServiceType = userServiceTypeArray.getJSONObject(0).getString("value");
		}
		for(String preferredLanguage:preferredLanguages) {
			attributes.put(nameArray[0] + "_" + preferredLanguage, "applicant");

			String userService = "";
			if("Alien New Registration".equalsIgnoreCase(userServiceType)) {
				userService = userServiceType;
			} else if ("Renewal of Alien".equalsIgnoreCase(userServiceType)) {
				userService = userServiceType;
			} else if ("Alien Replacement".equalsIgnoreCase(userServiceType)) {
				userService = userServiceType;
			} else if ("NEW".equals(regType)) {
				userService = "New Registration";
			} else if ("LOST".equals(regType)) {
				userService = "Replacement Of Card";
			} else if ("UPDATE".equals(regType)) {
				userService = "Change Of Particulars";
			} else if ("RENEWAL".equals(regType)) {
				userService = "Renewal Of Card";
			}else if ("FIRSTID".equals(regType)) {
				userService = "Get First ID";
			}
			attributes.put("SERVICE", userService);

			DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
			DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mma");

			LocalDateTime now = LocalDateTime.now();
			Instant nowUtc = Instant.now();
			ZoneId countryZoneId = ZoneId.of(timeZone);
			ZonedDateTime nowCountryTime = ZonedDateTime.ofInstant(nowUtc, countryZoneId);

			attributes.put("DATE", dateFormatter.format(now));
			attributes.put("TIME", timeFormatter.format(nowCountryTime));

			if (isProcessingSuccess) {
				type = setNotificationTemplateType(registrationStatusDto, type);
			} else if (!isValidSupervisorStatus) {
				type = NotificationTemplateType.SUP_REJECT;
			} else {
				type = NotificationTemplateType.TECHNICAL_ISSUE;
			}
			if (type != null) {
				setTemplateAndSubject(type, regType, messageSenderDTO);
			}
			//
			String countryCodeVal = packetManagerService.getField(registrationId, "CountryCode", regType, ProviderStageName.PACKET_VALIDATOR);
			String countryCode = null;
			if (countryCodeVal != null) {
				JSONArray countryCodeArray = new JSONArray(countryCodeVal);
				countryCode = countryCodeArray.getJSONObject(0).getString("value");
			}
			String residenceStatusPacketVal = packetManagerService.getField(registrationId, "residenceStatus", regType, ProviderStageName.PACKET_VALIDATOR);
			String residenceStatus = null;
			if (residenceStatusPacketVal != null) {
				JSONArray residenceStatusArray = new JSONArray(residenceStatusPacketVal);
				residenceStatus = residenceStatusArray.getJSONObject(0).getString("value");
			}
			//
			if (allNotificationTypes != null) {
				for (String notificationType : allNotificationTypes) {
					if (notificationType.equalsIgnoreCase("EMAIL")
							&& (registrationAdditionalInfoDTO.getEmail() != null
							&& !registrationAdditionalInfoDTO.getEmail().isEmpty())) {
						if (registrationStatusDto.getRegistrationType().equals("UPDATE") || enableEmailForOtherProcess || (residenceStatus != null && residenceStatus.equals("Outside Uganda"))) {
							regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
									LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
									"enteredenableEmailForOtherProcess" + enableEmailForOtherProcess);
							sendEmailNotification(registrationAdditionalInfoDTO, messageSenderDTO, attributes,
									description, preferredLanguage, registrationId);
						}
					} else if (notificationType.equalsIgnoreCase("SMS") && (registrationAdditionalInfoDTO.getPhone() != null
							&& !registrationAdditionalInfoDTO.getPhone().isEmpty()) && "Uganda (256)".equals(countryCode)) {
						sendSMSNotification(registrationAdditionalInfoDTO, messageSenderDTO, attributes, description,
								preferredLanguage, registrationId);
					}
				}
			}
		}
	}

	private List<String> getPreferredLanguages(InternalRegistrationStatusDto registrationStatusDto) throws ApisResourceAccessException, 
	PacketManagerException, JsonProcessingException, IOException, JSONException {
		if(userPreferredLanguageAttribute!=null && !userPreferredLanguageAttribute.isBlank()) {
			try {
			String preferredLang=packetManagerService.getField(registrationStatusDto.getRegistrationId(), userPreferredLanguageAttribute,
				registrationStatusDto.getRegistrationType(), ProviderStageName.PACKET_VALIDATOR);
				if(preferredLang!=null && !preferredLang.isBlank()) {
					List<String> codes=new ArrayList<>();
					for(String lang:preferredLang.split(",")) {
						String langCode=languageUtility.getLangCodeFromNativeName(lang);
						if(langCode!=null &&!langCode.isBlank())
							codes.add(langCode);
					}
					if(!codes.isEmpty())return codes;
				}
			}catch(ApisResourceAccessException e) {
				regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationStatusDto.getRegistrationId(), PlatformErrorMessages.RPR_PGS_API_RESOURCE_NOT_AVAILABLE.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			}
		}
		if(defaultTemplateLanguages!=null && !defaultTemplateLanguages.isBlank()) {
			return List.of(defaultTemplateLanguages.split(","));
		}
		Map<String,String> idValuesMap=packetManagerService.getAllFieldsByMappingJsonKeys(registrationStatusDto.getRegistrationId(), 
				registrationStatusDto.getRegistrationType(), ProviderStageName.PACKET_VALIDATOR);
		List<String> idValues=new ArrayList<>();
		for(Entry<String, String> entry: idValuesMap.entrySet()) {
		      	if(entry.getValue()!=null && !entry.getValue().isBlank()) {
		        	idValues.add(entry.getValue());
		        }
		}
		Set<String> langSet=new HashSet<>();
		for( String idValue:idValues) {
			if(idValue!=null&& !idValue.isBlank()  ) {
				if(isJSONArrayValid(idValue)) {
					org.json.simple.JSONArray array=mapper.readValue(idValue, org.json.simple.JSONArray.class);
					for(Object obj:array) {	
						org.json.simple.JSONObject json= new org.json.simple.JSONObject((Map) obj);
						langSet.add( (String) json.get("language"));	
					}
				}
			}
		}
		return new ArrayList<>(langSet);
	}
	
	public boolean isJSONArrayValid(String jsonArrayString) {
	        try {
	            new JSONArray(jsonArrayString);
	        } catch (JSONException ex) {
	            return false;
	        }
	    return true;
	}

	private void sendSMSNotification(RegistrationAdditionalInfoDTO registrationAdditionalInfoDTO,
			MessageSenderDTO messageSenderDTO, Map<String, Object> attributes, LogDescription description,
			String preferedLanguage, String registrationId) {
		try {
			sendSMS(registrationAdditionalInfoDTO,
					messageSenderDTO.getSmsTemplateCode(), attributes, preferedLanguage, registrationId);
		} catch (IOException | JSONException | ApisResourceAccessException e) {
			description.setCode(PlatformErrorMessages.RPR_MESSAGE_SENDER_SMS_FAILED.getCode());
			description.setMessage(StatusUtil.MESSAGE_SENDER_SMS_FAILED.getMessage());
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), description.getCode(), registrationId,
					description + e.getMessage() + ExceptionUtils.getStackTrace(e));
		}
	}

	private void sendSMS(RegistrationAdditionalInfoDTO registrationAdditionalInfoDTO, String templateTypeCode,
			Map<String, Object> attributes, String preferedLanguage, String registrationId)
			throws ApisResourceAccessException, IOException, JSONException {

		SmsRequestDto smsDto = new SmsRequestDto();
		RequestWrapper<SmsRequestDto> requestWrapper = new RequestWrapper<>();

		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(),
				registrationId, "NotificationUtility::sendSms()::entry");
		try {
			InputStream in = templateGenerator.getTemplate(templateTypeCode, attributes, preferedLanguage);
			String artifact = IOUtils.toString(in, ENCODING);

			smsDto.setNumber(registrationAdditionalInfoDTO.getPhone());
			smsDto.setMessage(artifact);

			requestWrapper.setId(env.getProperty(SMS_SERVICE_ID));
			requestWrapper.setVersion(env.getProperty(REG_PROC_APPLICATION_VERSION));
			DateTimeFormatter format = DateTimeFormatter.ofPattern(env.getProperty(DATETIME_PATTERN));
			LocalDateTime localdatetime = LocalDateTime
					.parse(DateUtils.getUTCCurrentDateTimeString(env.getProperty(DATETIME_PATTERN)), format);
			requestWrapper.setRequesttime(localdatetime);
			requestWrapper.setRequest(smsDto);
			regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(),
					registrationId, "NotificationUtility::sendSms():: SMSNOTIFIER POST service started with request : "
							+ JsonUtil.objectMapperObjectToJson(requestWrapper));

			AsyncRequestDTO request = new AsyncRequestDTO();
			request.setUri(env.getProperty(ApiName.SMSNOTIFIER.toString()));
			request.setHttpMethod(HttpMethod.POST);
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			request.setHeaders(headers);
			request.setRequestBody(requestWrapper);
			request.setResponseType(ResponseWrapper.class);
			restHelper.requestFireAndForget(request);
		} catch (TemplateNotFoundException | TemplateProcessingFailureException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationId, PlatformErrorMessages.RPR_SMS_TEMPLATE_GENERATION_FAILURE.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new TemplateGenerationFailedException(
					PlatformErrorMessages.RPR_SMS_TEMPLATE_GENERATION_FAILURE.getCode(), e);
		} catch (ApisResourceAccessException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationId, PlatformErrorMessages.RPR_PGS_API_RESOURCE_NOT_AVAILABLE.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new ApisResourceAccessException(PlatformErrorMessages.RPR_PGS_API_RESOURCE_NOT_AVAILABLE.name(), e);
		}
		catch (RuntimeException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationId,
					"NotificationUtility::sendSms()::Runtime exception occurred " + ExceptionUtils.getStackTrace(e));

		} catch (Exception e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationId,
					"NotificationUtility::sendSms()::Exception occurred " + ExceptionUtils.getStackTrace(e));
		}

	}

	private void sendEmailNotification(RegistrationAdditionalInfoDTO registrationAdditionalInfoDTO,
			MessageSenderDTO messageSenderDTO, Map<String, Object> attributes, LogDescription description,
			String preferedLanguage, String registrationId) {
		try {
			String subjectTemplateCode = messageSenderDTO.getSubjectTemplateCode();
			
			ResponseDto emailResponse = sendEmail(registrationAdditionalInfoDTO,
					messageSenderDTO.getEmailTemplateCode(), subjectTemplateCode, attributes, preferedLanguage,
					registrationId);
			if (emailResponse.getStatus().equals("success")) {
				description.setCode(PlatformSuccessMessages.RPR_MESSAGE_SENDER_STAGE_SUCCESS.getCode());
				description.setMessage(StatusUtil.MESSAGE_SENDER_EMAIL_SUCCESS.getMessage());
				regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
						description.getCode() + description.getMessage());
			} else {
				description.setCode(PlatformErrorMessages.RPR_MESSAGE_SENDER_EMAIL_FAILED.getCode());
				description.setMessage(StatusUtil.MESSAGE_SENDER_EMAIL_FAILED.getMessage());
				regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
						description.getCode() + description.getMessage());
			}
		} catch (Exception e) {
			description.setCode(PlatformErrorMessages.RPR_MESSAGE_SENDER_EMAIL_FAILED.getCode());
			description.setMessage(PlatformErrorMessages.RPR_MESSAGE_SENDER_EMAIL_FAILED.getMessage());
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), description.getCode(), registrationId,
					description + e.getMessage() + ExceptionUtils.getStackTrace(e));
		}
	}

	private ResponseDto sendEmail(RegistrationAdditionalInfoDTO registrationAdditionalInfoDTO, String templateTypeCode,
			String subjectTypeCode, Map<String, Object> attributes, String preferedLanguage, String registrationId)
			throws Exception {
		ResponseDto response = null;

		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(),
				registrationId, "NotificationUtility::sendEmail()::entry");
		try {
			InputStream in = templateGenerator.getTemplate(templateTypeCode, attributes, preferedLanguage);
			String artifact = IOUtils.toString(in, ENCODING);

			InputStream subjectInputStream = templateGenerator.getTemplate(subjectTypeCode, attributes, preferedLanguage);
			String subjectArtifact = IOUtils.toString(subjectInputStream, ENCODING);

			String mailTo = registrationAdditionalInfoDTO.getEmail();

			response = sendEmail(mailTo, subjectArtifact, artifact);

		} catch (TemplateNotFoundException | TemplateProcessingFailureException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationId, PlatformErrorMessages.RPR_SMS_TEMPLATE_GENERATION_FAILURE.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new TemplateGenerationFailedException(
					PlatformErrorMessages.RPR_SMS_TEMPLATE_GENERATION_FAILURE.getCode(), e);
		} catch (ApisResourceAccessException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationId, PlatformErrorMessages.RPR_PGS_API_RESOURCE_NOT_AVAILABLE.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new ApisResourceAccessException(PlatformErrorMessages.RPR_PGS_API_RESOURCE_NOT_AVAILABLE.name(), e);
		}
		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(),
				registrationId, "NotificationUtility::sendEmail()::exit");

		return response;
	}

	private ResponseDto sendEmail(String mailTo, String subjectArtifact, String artifact) throws Exception {
		LinkedMultiValueMap<String, Object> params = new LinkedMultiValueMap<>();
		ResponseWrapper<?> responseWrapper;
		ResponseDto responseDto = null;
		String apiHost = env.getProperty(ApiName.EMAILNOTIFIER.name());
		UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiHost);

		builder.queryParam("mailTo", mailTo);

		builder.queryParam("mailSubject", subjectArtifact);
		builder.queryParam("mailContent", artifact);

		params.add("attachments", null);

		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), "",
				"NotificationUtility::sendEmail():: EMAILNOTIFIER POST service started");

		responseWrapper = (ResponseWrapper<?>) resclient.postApi(builder.build().toUriString(),
				MediaType.MULTIPART_FORM_DATA, params, ResponseWrapper.class);

		responseDto = mapper.readValue(mapper.writeValueAsString(responseWrapper.getResponse()), ResponseDto.class);
		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), "",
				"NotificationUtility::sendEmail():: EMAILNOTIFIER POST service ended with in response : "
						+ JsonUtil.objectMapperObjectToJson(responseDto));

		return responseDto;
	}

	private NotificationTemplateType setNotificationTemplateType(InternalRegistrationStatusDto registrationStatusDto,
			NotificationTemplateType type) {
		if (registrationStatusDto.getRegistrationType().equalsIgnoreCase(SyncTypeDto.LOST.getValue()))
			type = NotificationTemplateType.LOST_UIN;
		else if (registrationStatusDto.getRegistrationType().equalsIgnoreCase(SyncTypeDto.NEW.getValue()))
			type = NotificationTemplateType.NEW_REG;
		else if (registrationStatusDto.getRegistrationType().equalsIgnoreCase(SyncTypeDto.UPDATE.getValue()))
			type = NotificationTemplateType.UIN_UPDATE;
		else if (registrationStatusDto.getRegistrationType().equalsIgnoreCase(SyncTypeDto.FIRSTID.getValue()))
			type = NotificationTemplateType.GET_FIRSTID;
		else if (registrationStatusDto.getRegistrationType().equalsIgnoreCase(SyncTypeDto.RENEWAL.getValue()))
			type = NotificationTemplateType.UIN_RENEWAL;
		else if (registrationStatusDto.getRegistrationType().equalsIgnoreCase(SyncTypeDto.RES_REPRINT.getValue()))
			type = NotificationTemplateType.REPRINT_UIN;
		else if (registrationStatusDto.getRegistrationType().equalsIgnoreCase(SyncTypeDto.ACTIVATED.getValue()))
			type = NotificationTemplateType.ACTIVATE;
		else if (registrationStatusDto.getRegistrationType().equalsIgnoreCase(SyncTypeDto.DEACTIVATED.getValue()))
			type = NotificationTemplateType.DEACTIVATE;
		else if (registrationStatusDto.getRegistrationType().equalsIgnoreCase(SyncTypeDto.RES_UPDATE.getValue()))
			type = NotificationTemplateType.RES_UPDATE;
		return type;
	}

	private void setTemplateAndSubject(NotificationTemplateType templatetype, String regType,
			MessageSenderDTO MessageSenderDTO) {
		switch (templatetype) {
		case NEW_REG:
			MessageSenderDTO.setSmsTemplateCode(env.getProperty(NEW_REG+SMS));
			MessageSenderDTO.setEmailTemplateCode(env.getProperty(NEW_REG+EMAIL));
			MessageSenderDTO.setSubjectTemplateCode(env.getProperty(NEW_REG+SUB));
			break;
		case LOST_UIN:
			MessageSenderDTO.setSmsTemplateCode(env.getProperty(LOST_UIN+SMS));
			MessageSenderDTO.setEmailTemplateCode(env.getProperty(LOST_UIN+EMAIL));
			MessageSenderDTO.setSubjectTemplateCode(env.getProperty(LOST_UIN+SUB));
			break;
		case UIN_UPDATE:
			MessageSenderDTO.setSmsTemplateCode(env.getProperty(UIN_UPDATE+SMS));
			MessageSenderDTO.setEmailTemplateCode(env.getProperty(UIN_UPDATE+EMAIL));
			MessageSenderDTO.setSubjectTemplateCode(env.getProperty(UIN_UPDATE+SUB));
			break;
		case UIN_RENEWAL:
			MessageSenderDTO.setSmsTemplateCode(env.getProperty(UIN_RENEWAL+SMS));
			MessageSenderDTO.setEmailTemplateCode(env.getProperty(UIN_RENEWAL+EMAIL));
			MessageSenderDTO.setSubjectTemplateCode(env.getProperty(UIN_RENEWAL+SUB));
			break;
		case REPRINT_UIN:
			MessageSenderDTO.setSmsTemplateCode(env.getProperty(REPRINT_UIN+SMS));
			MessageSenderDTO.setEmailTemplateCode(env.getProperty(REPRINT_UIN+EMAIL));
			MessageSenderDTO.setSubjectTemplateCode(env.getProperty(REPRINT_UIN+SUB));
			break;
		case ACTIVATE:
			MessageSenderDTO.setSmsTemplateCode(env.getProperty(ACTIVATE+SMS));
			MessageSenderDTO.setEmailTemplateCode(env.getProperty(ACTIVATE+EMAIL));
			MessageSenderDTO.setSubjectTemplateCode(env.getProperty(ACTIVATE+SUB));
			break;
		case DEACTIVATE:
			MessageSenderDTO.setSmsTemplateCode(env.getProperty(DEACTIVATE+SMS));
			MessageSenderDTO.setEmailTemplateCode(env.getProperty(DEACTIVATE+EMAIL));
			MessageSenderDTO.setSubjectTemplateCode(env.getProperty(DEACTIVATE+SUB));
			break;
		case RES_UPDATE:
			MessageSenderDTO.setSmsTemplateCode(env.getProperty(RES_UPDATE+SMS));
			MessageSenderDTO.setEmailTemplateCode(env.getProperty(RES_UPDATE+EMAIL));
			MessageSenderDTO.setSubjectTemplateCode(env.getProperty(RES_UPDATE+SUB));
			break;
		case TECHNICAL_ISSUE:
			MessageSenderDTO.setSmsTemplateCode(env.getProperty(TECHNICAL_ISSUE+SMS));
			MessageSenderDTO.setEmailTemplateCode(env.getProperty(TECHNICAL_ISSUE+EMAIL));
			MessageSenderDTO.setSubjectTemplateCode(env.getProperty(TECHNICAL_ISSUE+SUB));
			break;
		case SUP_REJECT:
			MessageSenderDTO.setSmsTemplateCode(env.getProperty(SUP_REJECT+SMS));
			MessageSenderDTO.setEmailTemplateCode(env.getProperty(SUP_REJECT+EMAIL));
			MessageSenderDTO.setSubjectTemplateCode(env.getProperty(SUP_REJECT+SUB));
			break;
		case GET_FIRSTID:
			MessageSenderDTO.setSmsTemplateCode(env.getProperty(GET_FIRSTID + SMS));
			MessageSenderDTO.setEmailTemplateCode(env.getProperty(GET_FIRSTID + EMAIL));
			MessageSenderDTO.setSubjectTemplateCode(env.getProperty(GET_FIRSTID + SUB));
			break;
		default:
			break;
		}
	}
}
