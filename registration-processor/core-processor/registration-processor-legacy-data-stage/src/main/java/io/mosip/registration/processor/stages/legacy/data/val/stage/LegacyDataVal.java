package io.mosip.registration.processor.stages.legacy.data.val.stage;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.code.ApiName;
import io.mosip.registration.processor.core.code.RegistrationTransactionStatusCode;
import io.mosip.registration.processor.core.common.rest.dto.ErrorDTO;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.DataMigrationPacketCreationException;
import io.mosip.registration.processor.core.exception.LegacyDataBiomtericException;
import io.mosip.registration.processor.core.exception.LegacyDataValidationException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.exception.ValidationFailedException;
import io.mosip.registration.processor.core.exception.util.PlatformSuccessMessages;
import io.mosip.registration.processor.core.http.RequestWrapper;
import io.mosip.registration.processor.core.http.ResponseWrapper;
import io.mosip.registration.processor.core.logger.LogDescription;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.migration.dto.MigrationRequestUpdateDto;
import io.mosip.registration.processor.core.migration.dto.MigrationResponse;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.core.status.util.StatusUtil;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.core.util.RegistrationExceptionMapperUtil;
import io.mosip.registration.processor.packet.storage.utils.FingrePrintConvertor;
import io.mosip.registration.processor.packet.storage.utils.LegacyDataApiUtility;
import io.mosip.registration.processor.packet.storage.utils.PriorityBasedPacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.stages.legacy.data.val.dto.Body;
import io.mosip.registration.processor.stages.legacy.data.val.dto.Envelope;
import io.mosip.registration.processor.stages.legacy.data.val.dto.Fingerprint;
import io.mosip.registration.processor.stages.legacy.data.val.dto.Header;
import io.mosip.registration.processor.stages.legacy.data.val.dto.IdentifyPerson;
import io.mosip.registration.processor.stages.legacy.data.val.dto.IdentifyPersonResponse;
import io.mosip.registration.processor.stages.legacy.data.val.dto.Password;
import io.mosip.registration.processor.stages.legacy.data.val.dto.Person;
import io.mosip.registration.processor.stages.legacy.data.val.dto.Position;
import io.mosip.registration.processor.stages.legacy.data.val.dto.Request;
import io.mosip.registration.processor.stages.legacy.data.val.dto.TransactionStatus;
import io.mosip.registration.processor.stages.legacy.data.val.dto.UsernameToken;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.dto.SyncRegistrationDto;
import io.mosip.registration.processor.status.dto.SyncResponseDto;
import io.mosip.registration.processor.status.service.RegistrationStatusService;
import io.mosip.registration.processor.status.service.SyncRegistrationService;

@Service
public class LegacyDataVal {
	private static Logger regProcLogger = RegProcessorLogger.getLogger(LegacyDataVal.class);

	public static final String INDIVIDUAL_TYPE_UIN = "UIN";

	private static final String ID = "mosip.commmons.packetmanager";
	private static final String VERSION = "v1";

	@Autowired
	RegistrationExceptionMapperUtil registrationExceptionMapperUtil;

	@Autowired
	private PriorityBasedPacketManagerService packetManagerService;


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
	private ObjectMapper objectMapper;

	@Value("${mosip.regproc.legacydata.validator.tpi.username}")
	private String username;

	public void validate(String registrationId, InternalRegistrationStatusDto registrationStatusDto,
			LogDescription description, MessageDTO object)
			throws ApisResourceAccessException, PacketManagerException, JsonProcessingException, IOException,
			ValidationFailedException, JAXBException, NoSuchAlgorithmException,
			NumberFormatException, JSONException, DataMigrationPacketCreationException, LegacyDataValidationException,
			LegacyDataBiomtericException {

		regProcLogger.debug("validate called for registrationId {}", registrationId);

			Map<String, String> positionAndWsqMap = getBiometricsWSQFormat(registrationId, registrationStatusDto);
			String NIN = checkNINAVailableInLegacy(registrationId, positionAndWsqMap);
			if (NIN != null) {
				regProcLogger.info("Single NIN is present in legacy system and call for ondemand migration : {}",
						registrationId);
				MigrationRequestUpdateDto migrationRequestUpdateDto = new MigrationRequestUpdateDto();
				migrationRequestUpdateDto.setNin(NIN.toUpperCase());
				migrationRequestUpdateDto.setDependentRid(registrationId);
				RequestWrapper<MigrationRequestUpdateDto> requestWrapper = new RequestWrapper();
				requestWrapper.setRequest(migrationRequestUpdateDto);
					ResponseWrapper responseWrapper = (ResponseWrapper<?>) restApi
						.postApi(ApiName.MIGARTION_URL_NEW, "", "", requestWrapper, ResponseWrapper.class,
									null);
					regProcLogger.info("Response from migration api : {}{}", registrationId,
							JsonUtils.javaObjectToJsonString(responseWrapper));
					if (responseWrapper.getErrors() != null && responseWrapper.getErrors().size() > 0) {
						ErrorDTO error = (ErrorDTO) responseWrapper.getErrors().get(0);
						throw new DataMigrationPacketCreationException(error.getErrorCode(),
								error.getMessage() + " matchedNIN " + NIN);
					}
					MigrationResponse migrationResponse = objectMapper
							.readValue(
							JsonUtils.javaObjectToJsonString(responseWrapper.getResponse()),
									MigrationResponse.class);
					if (migrationResponse != null) {
						regProcLogger.info(
								"ondemand migration happended for registration id  and migration rid is  : {} {}",
								registrationId, migrationResponse.getRid());
						throw new ValidationFailedException(StatusUtil.LEGACY_DATA_FAILED.getCode(),
								StatusUtil.LEGACY_DATA_FAILED.getMessage() + " matchedNIN " + NIN);
					} else {
						regProcLogger.info("ondemand migration api response is null  for registration id : {}",
								registrationId);
						throw new DataMigrationPacketCreationException(
								StatusUtil.LEGACY_DATA_MIGRATION_API_FAILED.getCode(),
								StatusUtil.LEGACY_DATA_MIGRATION_API_FAILED.getMessage() + " matchedNIN " + NIN);
					}

			} else {
				regProcLogger.info("NIN is not present in legacy system so proceed for new registration: {}",
						registrationId);
				registrationStatusDto
						.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.SUCCESS.toString());
				registrationStatusDto.setStatusComment(StatusUtil.LEGACY_DATA_SUCCESS.getMessage());
				registrationStatusDto.setSubStatusCode(StatusUtil.LEGACY_DATA_SUCCESS.getCode());
				registrationStatusDto.setStatusCode(RegistrationStatusCode.PROCESSING.toString());

				description.setMessage(
						PlatformSuccessMessages.RPR_LEGACY_DATA_VALIDATE.getMessage() + " -- " + registrationId);
				description.setCode(PlatformSuccessMessages.RPR_LEGACY_DATA_VALIDATE.getCode());
			}

		regProcLogger.debug("validate call ended for registrationId {}", registrationId);

	}

	private Map<String, String> getBiometricsWSQFormat(String registrationId,
			InternalRegistrationStatusDto registrationStatusDto)
			throws IOException, ApisResourceAccessException, PacketManagerException, JsonProcessingException,
			LegacyDataBiomtericException
	{
		
		JSONObject regProcessorIdentityJson = utility
				.getRegistrationProcessorMappingJson(MappingJsonConstants.IDENTITY);
		String individualBiometricsLabel = JsonUtil.getJSONValue(
				JsonUtil.getJSONObject(regProcessorIdentityJson, MappingJsonConstants.INDIVIDUAL_BIOMETRICS),
				MappingJsonConstants.VALUE);
		List<String> modalities = new ArrayList<>();
		modalities.add("Finger");
		BiometricRecord biometricRecord = packetManagerService.getBiometrics(registrationId,
				individualBiometricsLabel,
				modalities, registrationStatusDto.getRegistrationType(),
				ProviderStageName.LEGACY_DATA);
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

	private String checkNINAVailableInLegacy(String registrationId, Map<String, String> positionAndWsqMap)
			throws JAXBException, ApisResourceAccessException, NoSuchAlgorithmException, UnsupportedEncodingException,
			ValidationFailedException, LegacyDataValidationException {
		String NIN = null;
		Envelope requestEnvelope = createIdentifyPersonRequest(positionAndWsqMap);
		String request = marshalToXml(requestEnvelope);
		regProcLogger.debug("Request to legacy system : {}", request);
		String response = (String) restApi.postApi(ApiName.LEGACYAPI, "", "", request, String.class,
				MediaType.TEXT_XML);
		regProcLogger.info("Response from legacy system : {}{}", registrationId,
				response);
		JAXBContext jaxbContext = JAXBContext.newInstance(Envelope.class);
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
		StringReader reader = new StringReader(response);
		Envelope responseEnvelope = (Envelope) unmarshaller.unmarshal(reader);
		IdentifyPersonResponse identifyPersonResponse = responseEnvelope.getBody().getIdentifyPersonResponse();
		TransactionStatus transactionStatus = identifyPersonResponse.getReturnElement().getTransactionStatus();
		if (transactionStatus.getTransactionStatus().equalsIgnoreCase("Ok")) {
			List<Person> persons = identifyPersonResponse.getReturnElement().getPersons();
			if (persons != null && !persons.isEmpty()) {

				List<String> finalNins = persons.stream().map(Person::getNationalId).filter(Objects::nonNull)
						.filter(nin -> !(nin.startsWith("nct") || nin.startsWith("tmp"))).collect(Collectors.toList());
				if (finalNins != null && !finalNins.isEmpty()) {
					if (finalNins.size() == 1) {
					regProcLogger.info("Single nin returned from legacy : {}", registrationId);
					NIN = finalNins.get(0);
				} else {
					String nins = String.join(", ", finalNins);
					regProcLogger.error("Multiple NINs returned from legacy for regId {} : {}", registrationId, nins);
					throw new ValidationFailedException(StatusUtil.LEGACY_DATA_FAILED.getCode(),
							StatusUtil.LEGACY_DATA_FAILED.getMessage() + " matchedNINs " + nins);
				}
			}else {
				regProcLogger.info("No  nins returned from legacy : {}", registrationId);
			   }
			} else {
				regProcLogger.info("No  nins returned from legacy : {}", registrationId);
			}
			
		} else if (transactionStatus.getTransactionStatus().equalsIgnoreCase("Error")) {
			regProcLogger.info("Transaction status is Error : {}", registrationId);
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					registrationId,
					RegistrationStatusCode.FAILED.toString() + transactionStatus.getError().getCode()
							+ transactionStatus.getError().getMessage());
			throw new LegacyDataValidationException(transactionStatus.getError().getCode(),
					transactionStatus.getError().getMessage());
		}
		return NIN;
	}

	private Envelope createIdentifyPersonRequest(Map<String, String> positionAndWsqMap)
			throws NoSuchAlgorithmException, UnsupportedEncodingException {

		byte[] nonceBytes = legacyDataApiUtility.generateNonce();
		String nonce = CryptoUtil.encodeToPlainBase64(nonceBytes);

		String timestamp = legacyDataApiUtility.createTimestamp();
		String timestampForDigest = legacyDataApiUtility.createTimestampForDigest(timestamp);
		String timestampForRequest = timestamp;
		byte[] createdDigestBytes = timestampForDigest.getBytes(StandardCharsets.UTF_8);
		regProcLogger.info("timestamp  timestampForDigest timestampForRequest  registration id : {} {} {}", timestamp,
				timestampForDigest, timestampForRequest);
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
		IdentifyPerson identifyPerson = new IdentifyPerson();
		Request request = new Request();
		List<Fingerprint> fingerprints = new ArrayList<Fingerprint>();
		for (Map.Entry<String, String> entry : positionAndWsqMap.entrySet()) {
			Fingerprint fingerprint = new Fingerprint();
			fingerprint.setPosition(entry.getKey());
			fingerprint.setWsq(entry.getValue());
			fingerprints.add(fingerprint);
		}
		request.setFingerprints(fingerprints);
		identifyPerson.setRequest(request);
		body.setIdentifyPerson(identifyPerson);
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
}
