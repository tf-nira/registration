package io.mosip.registration.processor.stages.legacy.data.val.stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
import io.mosip.registration.processor.core.migration.dto.MigrationOnDemandResponse;
import io.mosip.registration.processor.core.migration.dto.MigrationRequestDto;
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
import io.mosip.registration.processor.stages.legacy.data.val.dto.IdentifyPersonGraphQLResponse;
import io.mosip.registration.processor.stages.legacy.data.val.dto.IdentifyPersonRequest;
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
	
	@Value("${graphql.post.urls}")
	private String postUrl;
	
	@Value("${graphql.auth.token}")
	private String authToken;

	private static final Gson GSON = new Gson();
	
	public void validate(String registrationId, InternalRegistrationStatusDto registrationStatusDto,
			LogDescription description, MessageDTO object) throws ApisResourceAccessException, PacketManagerException,
			JsonProcessingException, LegacyDataBiomtericException, IOException, URISyntaxException {

		regProcLogger.debug("validate called for registrationId {}", registrationId);

		Map<String, String> positionAndWsqMap = getBiometricsWSQFormat(registrationId, registrationStatusDto);
		
		regProcLogger.info("Retrieved {} fingerprints for registrationId {}", 
	            positionAndWsqMap.size(), registrationId);
		
		
		String response = sendIdentifyPersonGraphQLRequest(positionAndWsqMap,registrationId);

		regProcLogger.info("GraphQL response for registrationId {}: {}", registrationId, response);
	    
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
				if (persons.size() == 1) {
					regProcLogger.info("Single nin returned from legacy : {}", registrationId);
					NIN = persons.get(0).getNationalId();
				} else {
					regProcLogger.error("Mulitple nins returned from legacy : {}", registrationId);
					throw new ValidationFailedException(StatusUtil.LEGACY_DATA_FAILED.getMessage(),
							StatusUtil.LEGACY_DATA_FAILED.getCode());
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

	private String sendIdentifyPersonGraphQLRequest(Map<String, String> positionAndWsqMap, String registrationId) 
	        throws IOException, URISyntaxException {
	    
	    regProcLogger.info("sendIdentifyPersonGraphQLRequest started with {} fingerprints", 
	            positionAndWsqMap.size());
	    
	    // Validation checks
	    if (positionAndWsqMap == null || positionAndWsqMap.isEmpty()) {
	        regProcLogger.error("No fingerprints provided for identification");
	        throw new IllegalArgumentException("Fingerprint map cannot be null or empty");
	    }
	    
	    if (postUrl == null || postUrl.isEmpty()) {
	        regProcLogger.error("GraphQL endpoint URL is not configured");
	        throw new IllegalStateException("GraphQL endpoint URL is missing");
	    }
	    
	    if (authToken == null || authToken.isEmpty()) {
	        regProcLogger.error("Authorization token is not configured");
	        throw new IllegalStateException("Authorization token is missing");
	    }
	    
	    List<Fingerprint> fingerprints = new ArrayList<>();
	    for (Map.Entry<String, String> entry : positionAndWsqMap.entrySet()) {
	        Fingerprint fingerprint = new Fingerprint();
	        fingerprint.setPosition(entry.getKey());
	        fingerprint.setWsq(entry.getValue());
	        fingerprints.add(fingerprint);
	        regProcLogger.debug("Added fingerprint for position: {}", entry.getKey());
	    }
	    
	    IdentifyPersonRequest identifyPersonRequest = new IdentifyPersonRequest();
	    identifyPersonRequest.setFingerprints(fingerprints);
	    identifyPersonRequest.setRequestId(registrationId);
	    identifyPersonRequest.setNationalId("");

	    HttpURLConnection conn = null;
	    
	    try {
	        // Convert to JSON
	    	String json = GSON.toJson(identifyPersonRequest);
	        
	        regProcLogger.info("GraphQL Request URL: {}", postUrl);
	        regProcLogger.info("GraphQL Request Payload: {}", json);
	        
	        conn = (HttpURLConnection) new URL(postUrl).openConnection();
	        conn.setRequestMethod("POST");
	        conn.setRequestProperty("Content-Type", "application/json");
	        conn.setRequestProperty("Accept", "application/json");
	        conn.setRequestProperty("Authorization", authToken);
	        conn.setDoOutput(true);
	        // Set timeouts
	        conn.setConnectTimeout(120000);
	        conn.setReadTimeout(150000);
	        regProcLogger.info("Sending GraphQL request to legacy system");
	        
	        // Write request body
	        try (OutputStream os = conn.getOutputStream()) {
	            os.write(json.getBytes(StandardCharsets.UTF_8));
	        }

	        // Get response code
	        int code = conn.getResponseCode();
	        
	        regProcLogger.info("Received response with status code: {}", code);
	        
	        // Read response
	        BufferedReader br = new BufferedReader(new InputStreamReader(
	                (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream(),
	                StandardCharsets.UTF_8));

	        StringBuilder sb = new StringBuilder();
	        String line;
	        while ((line = br.readLine()) != null) {
	            sb.append(line);
	        }
	        br.close();
	        
	        String responseString = sb.toString();
	        
	        regProcLogger.info("Raw Response: {}", responseString);
	        
	        if (code < 200 || code >= 300) {
	            regProcLogger.error("GraphQL request failed with status {}: {}", code, responseString);
	            throw new IOException("HTTP Error " + code + ": " + responseString);
	        }
	        
	        JsonElement responseElement = JsonParser.parseString(responseString);
	        JsonObject responseJson = responseElement.getAsJsonObject();
	        
	        if (responseJson.has("errors")) {
	            regProcLogger.error("GraphQL returned errors: {}", responseJson.get("errors").toString());
	            throw new IOException("GraphQL errors: " + responseJson.get("errors").toString());
	        }
	        
	        String prettyResponse = GSON.toJson(responseJson);
	        regProcLogger.info("GraphQL response received successfully");
	        regProcLogger.info("Formatted Response: {}", prettyResponse);
	        
	        return prettyResponse;
	        
	    } catch (IOException e) {
	        regProcLogger.error("IOException during GraphQL request: {}", e.getMessage(), e);
	        throw e;
	    } finally {
	        if (conn != null) {
	            conn.disconnect();
	        }
	        regProcLogger.info("sendIdentifyPersonGraphQLRequest completed");
	    }
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
	
	public void callOnDemandMigration(String registrationId, InternalRegistrationStatusDto registrationStatusDto,
			LogDescription description, MessageDTO object, String NIN)
			throws ApisResourceAccessException, PacketManagerException, JsonProcessingException, IOException,
			ValidationFailedException, JAXBException, NoSuchAlgorithmException,
			NumberFormatException, JSONException, DataMigrationPacketCreationException, LegacyDataValidationException,
			LegacyDataBiomtericException {

		regProcLogger.debug("validate called for registrationId {}", registrationId);

			//response from subscribe will handle this
			if (NIN != null) {
				regProcLogger.info("Single NIN is present in legacy system and call for ondemand migration : {}",
						registrationId);
					MigrationRequestDto migrationRequestDto = new MigrationRequestDto();
					migrationRequestDto.setNin(NIN.toUpperCase());
					RequestWrapper<MigrationRequestDto> requestWrapper = new RequestWrapper();
					requestWrapper.setRequest(migrationRequestDto);
					ResponseWrapper responseWrapper = (ResponseWrapper<?>) restApi
							.postApi(ApiName.MIGARTION_PACKET_CREATION, "", "", requestWrapper,
									ResponseWrapper.class,
									null);
					regProcLogger.info("Response from migration api : {}{}", registrationId,
							JsonUtils.javaObjectToJsonString(responseWrapper));
					if (responseWrapper.getErrors() != null && responseWrapper.getErrors().size() > 0) {
						ErrorDTO error = (ErrorDTO) responseWrapper.getErrors().get(0);
						throw new DataMigrationPacketCreationException(error.getErrorCode(), error.getMessage());
					}
					MigrationOnDemandResponse migrationOnDemandResponse = objectMapper
							.readValue(
							JsonUtils.javaObjectToJsonString(responseWrapper.getResponse()),
									MigrationOnDemandResponse.class);
					if (migrationOnDemandResponse != null) {
						regProcLogger.info(
								"ondemand migration happended for registration id  and migration rid is  : {} {}",
								registrationId, migrationOnDemandResponse.getRid());
						throw new ValidationFailedException(StatusUtil.LEGACY_DATA_FAILED.getMessage(),
								StatusUtil.LEGACY_DATA_FAILED.getCode());
					} else {
						regProcLogger.info("ondemand migration api response is null  for registration id : {}",
								registrationId);
						throw new DataMigrationPacketCreationException(
								StatusUtil.LEGACY_DATA_MIGRATION_API_FAILED.getMessage(),
								StatusUtil.LEGACY_DATA_MIGRATION_API_FAILED.getCode());
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
	
	public void onDemandMigration(String NIN, IdentifyPersonGraphQLResponse response,
			InternalRegistrationStatusDto registrationStatusDto, LogDescription description) throws ApisResourceAccessException, JsonProcessingException, DataMigrationPacketCreationException, JsonMappingException, com.fasterxml.jackson.core.JsonProcessingException, ValidationFailedException {
		 if (NIN != null) {
				regProcLogger.info("Single NIN is present in legacy system and call for ondemand migration : {}",
						response.getRequestId());
					MigrationRequestDto migrationRequestDto = new MigrationRequestDto();
					migrationRequestDto.setNin(NIN.toUpperCase());
					RequestWrapper<MigrationRequestDto> requestWrapper = new RequestWrapper();
					requestWrapper.setRequest(migrationRequestDto);
					ResponseWrapper responseWrapper = (ResponseWrapper<?>) restApi
							.postApi(ApiName.MIGARTION_PACKET_CREATION, "", "", requestWrapper,
									ResponseWrapper.class,
									null);
					regProcLogger.info("Response from migration api : {}{}", response.getRequestId(),
							JsonUtils.javaObjectToJsonString(responseWrapper));
					if (responseWrapper.getErrors() != null && responseWrapper.getErrors().size() > 0) {
						ErrorDTO error = (ErrorDTO) responseWrapper.getErrors().get(0);
						throw new DataMigrationPacketCreationException(error.getErrorCode(), error.getMessage());
					}
					MigrationOnDemandResponse migrationOnDemandResponse = objectMapper
							.readValue(
							JsonUtils.javaObjectToJsonString(responseWrapper.getResponse()),
									MigrationOnDemandResponse.class);
					if (migrationOnDemandResponse != null) {
						regProcLogger.info(
								"ondemand migration happended for registration id  and migration rid is  : {} {}",
								response.getRequestId(), migrationOnDemandResponse.getRid());
						throw new ValidationFailedException(StatusUtil.LEGACY_DATA_FAILED.getMessage(),
								StatusUtil.LEGACY_DATA_FAILED.getCode());
					} else {
						regProcLogger.info("ondemand migration api response is null  for registration id : {}",
								response.getRequestId());
						throw new DataMigrationPacketCreationException(
								StatusUtil.LEGACY_DATA_MIGRATION_API_FAILED.getMessage(),
								StatusUtil.LEGACY_DATA_MIGRATION_API_FAILED.getCode());
					}

			} else {
				regProcLogger.info("NIN is not present in legacy system so proceed for new registration: {}",
						response.getRequestId());
				registrationStatusDto
						.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.SUCCESS.toString());
				registrationStatusDto.setStatusComment(StatusUtil.LEGACY_DATA_SUCCESS.getMessage());
				registrationStatusDto.setSubStatusCode(StatusUtil.LEGACY_DATA_SUCCESS.getCode());
				registrationStatusDto.setStatusCode(RegistrationStatusCode.PROCESSING.toString());

				description.setMessage(
						PlatformSuccessMessages.RPR_LEGACY_DATA_VALIDATE.getMessage() + " -- " + response.getRequestId());
				description.setCode(PlatformSuccessMessages.RPR_LEGACY_DATA_VALIDATE.getCode());
			}
	 }
}
