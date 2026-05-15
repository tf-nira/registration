package io.mosip.registration.processor.packet.storage.utils;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import io.mosip.registration.processor.core.migration.dto.MigrationRequestUpdateDto;
import io.mosip.registration.processor.core.migration.dto.MigrationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.code.ApiName;
import io.mosip.registration.processor.core.common.rest.dto.ErrorDTO;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.DataMigrationPacketCreationException;
import io.mosip.registration.processor.core.exception.LegacyDataValidationException;
import io.mosip.registration.processor.core.http.RequestWrapper;
import io.mosip.registration.processor.core.http.ResponseWrapper;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.migration.dto.MigrationPacketCreationResponse;
import io.mosip.registration.processor.core.migration.dto.MigrationRequestDto;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.packet.storage.dto.GetPerson;
import io.mosip.registration.processor.packet.storage.dto.GetPersonBody;
import io.mosip.registration.processor.packet.storage.dto.GetPersonEnvelope;
import io.mosip.registration.processor.packet.storage.dto.GetPersonHeader;
import io.mosip.registration.processor.packet.storage.dto.GetPersonPassword;
import io.mosip.registration.processor.packet.storage.dto.GetPersonRequest;
import io.mosip.registration.processor.packet.storage.dto.GetPersonResponse;
import io.mosip.registration.processor.packet.storage.dto.GetPersonTransactionStatus;
import io.mosip.registration.processor.packet.storage.dto.GetPersonUsernameToken;
import io.mosip.registration.processor.packet.storage.dto.SecureZoneNotificationRequest;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.service.RegistrationStatusService;



@Component
public class MigrationUtil {

	private static Logger regProcLogger = RegProcessorLogger.getLogger(MigrationUtil.class);

	@Autowired
	private LegacyDataApiUtility legacyDataApiUtility;

	@Autowired
	private RegistrationProcessorRestClientService<Object> restApi;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

	@Value("${mosip.regproc.legacydata.validator.tpi.username}")
	private String username;

	public String validateAndCreateOnDemandPacket(String registrationId, String NIN)
			throws JAXBException, ApisResourceAccessException, NoSuchAlgorithmException, UnsupportedEncodingException,
			JsonProcessingException, JsonMappingException, com.fasterxml.jackson.core.JsonProcessingException,
			DataMigrationPacketCreationException, LegacyDataValidationException {
		boolean isValid = false;
		GetPersonEnvelope requestEnvelope = createGetPersonRequest(NIN);
		String request = marshalToXml(requestEnvelope);
		String response = (String) restApi.postApi(ApiName.LEGACYAPI, "", "", request, String.class,
				MediaType.TEXT_XML);
		regProcLogger.info("Response from legacy system : {}{}", registrationId, response);
		JAXBContext jaxbContext = JAXBContext.newInstance(GetPersonEnvelope.class);
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
		StringReader reader = new StringReader(response);
		GetPersonEnvelope responseEnvelope = (GetPersonEnvelope) unmarshaller.unmarshal(reader);
		GetPersonResponse getPersonResponse = responseEnvelope.getBody().getGetPersonResponse();
		GetPersonTransactionStatus transactionStatus = getPersonResponse.getReturnElement().getTransactionStatus();
		if (transactionStatus.getTransactionStatus().equalsIgnoreCase("Ok")) {
			regProcLogger.info("NIN is present in legacy system for applicant relative  : {}{}", registrationId,
					getPersonResponse.getReturnElement().getNationalId());
			if (getPersonResponse.getReturnElement().getNationalId() != null) {
				regProcLogger.info(
						"NIN is present in legacy system and call for ondemand migration  of applicant relative: {}",
						registrationId);
				MigrationRequestUpdateDto migrationRequestDto = new MigrationRequestUpdateDto();
				migrationRequestDto.setNin(NIN.toUpperCase());
				migrationRequestDto.setDependentRid(registrationId);
				RequestWrapper<MigrationRequestUpdateDto> requestWrapper = new RequestWrapper();
				requestWrapper.setRequest(migrationRequestDto);
				ResponseWrapper responseWrapper = (ResponseWrapper<?>) restApi.postApi(
						ApiName.MIGARTION_URL_NEW, "", "", requestWrapper, ResponseWrapper.class, null);
				regProcLogger.info("Response from migration api : {}{}", registrationId,
						JsonUtils.javaObjectToJsonString(responseWrapper));
				if (responseWrapper.getErrors() != null && responseWrapper.getErrors().size() > 0) {
					ErrorDTO error = (ErrorDTO) responseWrapper.getErrors().get(0);
					throw new DataMigrationPacketCreationException(error.getErrorCode(), error.getMessage());
				}
				MigrationResponse migrationResponse = objectMapper.readValue(
						JsonUtils.javaObjectToJsonString(responseWrapper.getResponse()),
						MigrationResponse.class);
				if (migrationResponse != null) {
					regProcLogger.info("ondemand migration happended for registration id : {}", registrationId);
					String migratedRegistrationId = migrationResponse.getRid();
					if (migratedRegistrationId != null) {
						isValid = true;
					InternalRegistrationStatusDto internalRegistrationStatusDto = registrationStatusService
							.getRegistrationStatus(migratedRegistrationId, "MIGRATOR", 1, null);
					if(internalRegistrationStatusDto!=null) {
						SecureZoneNotificationRequest secureZoneNotificationRequest = new SecureZoneNotificationRequest();
						secureZoneNotificationRequest.setReg_type("MIGRATOR");
						secureZoneNotificationRequest.setRid(migratedRegistrationId);
						secureZoneNotificationRequest
								.setWorkflowInstanceId(internalRegistrationStatusDto.getWorkflowInstanceId());
						secureZoneNotificationRequest.setIteration(internalRegistrationStatusDto.getIteration());
						secureZoneNotificationRequest.setValid(true);
						secureZoneNotificationRequest.setInternalError(false);
						String secureZoneResponse = (String) restApi.postApi(ApiName.SECURE_ZONE_URL, "", "",
								secureZoneNotificationRequest,
								String.class, MediaType.APPLICATION_JSON);
						regProcLogger.info("ondemand migration packet status  : {}", secureZoneResponse);
					}
					return migratedRegistrationId;
				}
				} else {
					regProcLogger.info("ondemand migration api response is null  for registration id : {}",
							registrationId);
				}
			}
		} else if (transactionStatus.getTransactionStatus().equalsIgnoreCase("Error")) {
			regProcLogger.info("Transaction status is Error : {}", registrationId);
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationId, RegistrationStatusCode.FAILED.toString() + transactionStatus.getError().getCode()
							+ transactionStatus.getError().getMessage());
			throw new LegacyDataValidationException(transactionStatus.getError().getCode(),
					transactionStatus.getError().getMessage());
		}
		return null;
	}

	private GetPersonEnvelope createGetPersonRequest(String NIN)
			throws NoSuchAlgorithmException, UnsupportedEncodingException {

		byte[] nonceBytes = legacyDataApiUtility.generateNonce();
		String nonce = CryptoUtil.encodeToPlainBase64(nonceBytes);

		String timestamp = legacyDataApiUtility.createTimestamp();
		String timestampForDigest = legacyDataApiUtility.createTimestampForDigest(timestamp);
		String timestampForRequest = timestamp;
		byte[] createdDigestBytes = timestampForDigest.getBytes(StandardCharsets.UTF_8);

		byte[] passwordHashBytes = legacyDataApiUtility.hashPassword();
		String passwordDigest = legacyDataApiUtility.generateDigest(nonceBytes, createdDigestBytes, passwordHashBytes);
		GetPersonEnvelope envelope = new GetPersonEnvelope();
		// Header
		GetPersonHeader header = new GetPersonHeader();
		GetPersonUsernameToken token = new GetPersonUsernameToken();
		token.setUsername(username);
		GetPersonPassword password = new GetPersonPassword();
		password.setType("PasswordDigest");
		password.setValue(passwordDigest);
		token.setPassword(password);
		token.setNonce(nonce);
		token.setCreated(timestampForRequest);
		header.setUsernameToken(token);
		envelope.setHeader(header);

		// Body
		GetPersonBody body = new GetPersonBody();
		GetPerson getPerson = new GetPerson();
		GetPersonRequest request = new GetPersonRequest();
		request.setNationalId(NIN.toUpperCase());
		getPerson.setRequest(request);
		body.setGetPerson(getPerson);
		envelope.setBody(body);
		return envelope;
	}

	private String marshalToXml(GetPersonEnvelope envelope) throws JAXBException {
		JAXBContext jaxbContext = JAXBContext.newInstance(GetPersonEnvelope.class);
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, false);

		// Use a StringWriter to capture the XML
		java.io.StringWriter sw = new java.io.StringWriter();
		marshaller.marshal(envelope, sw);
		return sw.toString();
	}
}
