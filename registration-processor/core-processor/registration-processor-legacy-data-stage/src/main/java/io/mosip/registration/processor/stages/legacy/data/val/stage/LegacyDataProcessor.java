package io.mosip.registration.processor.stages.legacy.data.val.stage;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.Protocol;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

//import org.java_websocket.client.WebSocketClient;
import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.abstractverticle.MessageBusAddress;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.code.ApiName;
import io.mosip.registration.processor.core.code.EventId;
import io.mosip.registration.processor.core.code.EventName;
import io.mosip.registration.processor.core.code.EventType;
import io.mosip.registration.processor.core.code.ModuleName;
import io.mosip.registration.processor.core.code.RegistrationExceptionTypeCode;
import io.mosip.registration.processor.core.code.RegistrationTransactionStatusCode;
import io.mosip.registration.processor.core.code.RegistrationTransactionTypeCode;
import io.mosip.registration.processor.core.common.rest.dto.ErrorDTO;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.DataMigrationPacketCreationException;
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
import io.mosip.registration.processor.core.status.util.StatusUtil;
import io.mosip.registration.processor.core.status.util.TrimExceptionMessage;
import io.mosip.registration.processor.core.util.RegistrationExceptionMapperUtil;
import io.mosip.registration.processor.packet.storage.exception.ParsingException;
import io.mosip.registration.processor.rest.client.audit.builder.AuditLogRequestBuilder;
import io.mosip.registration.processor.stages.legacy.data.val.dto.IdentifyPersonGraphQLResponse;
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
	
	@Autowired
	RegistrationExceptionMapperUtil registrationExceptionMapperUtil;
	
	@Autowired
	private LegacyDataStage legacyDataStage;
	
	private WebSocketClient client;
	
	@Value("${graphql.ws.url}")
	private String wsUrl;
	
	@Value("${graphql.auth.token}")
	private String authToken;
	
	@Value("${graphql.subscription.query}")
	private String subscriptionQuery;
	
	private final AtomicBoolean connected = new AtomicBoolean(false);
	
	private final Gson gson = new Gson();
	
	public MessageDTO process(MessageDTO object, String stageName) {
		LogDescription description = new LogDescription();
		boolean isTransactionSuccessful = false;
		String registrationId = "";
		//original code had the following Message Bus Address.
		//object.setMessageBusAddress(MessageBusAddress.INTRODUCER_VALIDATOR_BUS_IN);
		object.setMessageBusAddress(MessageBusAddress.LEGACY_DATA_IN);
		object.setIsValid(Boolean.FALSE);
		object.setInternalError(Boolean.TRUE);
		Map<String, String> attributes = new HashMap<>();
		registrationId = object.getRid();
		regProcLogger.debug("LegacyDataProcessor called for registrationId {}", registrationId);

		InternalRegistrationStatusDto registrationStatusDto = registrationStatusService
				.getRegistrationStatus(registrationId, object.getReg_type(), object.getIteration(), object.getWorkflowInstanceId());

		registrationStatusDto
				.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.LEGACY_DATA.toString());
		registrationStatusDto.setRegistrationStageName(stageName);
		//Setting the latest transaction status code (latest_trn_status_code) to IN_PROGRESS.
		registrationStatusDto.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.IN_PROGRESS.toString());
		registrationStatusDto.setSubStatusCode(StatusUtil.LEGACY_DATA_STAGE_IN_PROGRESS.getCode());
		registrationStatusDto.setStatusCode(RegistrationStatusCode.PROCESSING.toString());
		registrationStatusDto.setStatusComment(trimExpMessage.trimExceptionMessage(StatusUtil.LEGACY_DATA_STAGE_IN_PROGRESS.getMessage()));
		try {

			legacyDataVal.validate(registrationId, registrationStatusDto, description, object);
			regProcLogger.info("LegacyDataProcessor call ended for registrationId {} {} {}", registrationId,
					description.getCode() + description.getMessage());

			object.setIsValid(Boolean.TRUE);
			object.setInternalError(Boolean.FALSE);
			isTransactionSuccessful = true;
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
		} catch (IOException | URISyntaxException e) {
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
	
	public boolean isConnected() {
		return connected.get();
	}
	
	 private void sendConnectionInit() {
	        Map<String, Object> initPayload = new HashMap<>();
	        initPayload.put("Authorization", authToken);

	        Map<String, Object> initMsg = new HashMap<>();
	        initMsg.put("type", "connection_init");
	        initMsg.put("payload", initPayload);

	        client.send(gson.toJson(initMsg));
	        regProcLogger.info("→ Sent connection_init");
	    }
	 private void sendSubscribe() {
		 Map<String, Object> payload = new HashMap<>();
	        payload.put("query", subscriptionQuery);
	        payload.put("variables", new HashMap<>());

	        Map<String, Object> subMsg = new HashMap<>();
	        subMsg.put("id", "1");
	        subMsg.put("type", "subscribe");
	        subMsg.put("payload", payload);

	        client.send(gson.toJson(subMsg));
	        regProcLogger.info("→ Sent subscription start");
	 }
	 private String pretty(String json) {
	        try {
	            return gson.toJson(new JsonParser().parse(json));
	        } catch (Exception e) {
	            return json;
	        }
	    }
	 //method to do things things on getting result.
	 private void handleSubscriptionPayload(JsonObject msg) throws ValidationFailedException, LegacyDataValidationException, JsonMappingException, com.fasterxml.jackson.core.JsonProcessingException, ApisResourceAccessException, JsonProcessingException, DataMigrationPacketCreationException {
		 TrimExceptionMessage trimExceptionMessage = new TrimExceptionMessage();   
		 if (!msg.has("payload")) return;
	        JsonObject payload = msg.getAsJsonObject("payload");
	        if (!payload.has("data")) return;

	        JsonObject data = payload.getAsJsonObject("data");
	        regProcLogger.info("[SUB DATA] " + pretty(data.toString()));
	        
	        if(!data.has("identifyPerson")) {
	        	regProcLogger.info("[SUB DATA] Unknown Data : {}", data);
	        	return;
	        }
	        
	        JsonObject identifyPerson = data.getAsJsonObject("identifyPerson");
	        
	        Gson gson = new Gson();
	        IdentifyPersonGraphQLResponse response = gson.fromJson(identifyPerson, IdentifyPersonGraphQLResponse.class);
	        regProcLogger.info("[SUB DATA] : {}", response);
	        
	        String requestId = response.getRequestId();
	        LogDescription description = new LogDescription();
	        MessageDTO messageDTO = new MessageDTO();
            InternalRegistrationStatusDto registrationStatusDto = null;
            try {
            	registrationStatusDto = registrationStatusService.getRegistrationStatus(requestId,
            			null, null, null);
            	registrationStatusDto.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.LEGACY_DATA_VALIDATE.name());
    			//registrationStatusDto.setRegistrationStageName(stageName);
    			messageDTO.setInternalError(false);
    			messageDTO.setRid(requestId);
    			messageDTO.setReg_type(registrationStatusDto.getRegistrationType());
    			messageDTO.setWorkflowInstanceId(registrationStatusDto.getWorkflowInstanceId());
    			registrationStatusDto.setUpdatedBy(USER);
            } catch (Exception e) {
            	messageDTO.setInternalError(true);
    			registrationStatusDto.setLatestTransactionStatusCode(
    					registrationExceptionMapperUtil.getStatusCode(RegistrationExceptionTypeCode.EXCEPTION));
    			registrationStatusDto.setStatusComment(trimExceptionMessage
    					.trimExceptionMessage(StatusUtil.UNKNOWN_EXCEPTION_OCCURED.getMessage() + e.getMessage()));
    			registrationStatusDto.setSubStatusCode(StatusUtil.UNKNOWN_EXCEPTION_OCCURED.getCode());
    			registrationStatusDto.setStatusCode(RegistrationStatusCode.FAILED.toString());
    			description.setMessage(PlatformErrorMessages.UNKNOWN_EXCEPTION.getMessage());
    			description.setCode(PlatformErrorMessages.UNKNOWN_EXCEPTION.getCode());
            }
            registrationStatusDto.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.LEGACY_DATA.toString());
           // registrationStatusDto.setRegistrationStageName();
            
            
            handleIdentifyPersonGraphQLResponse(response, registrationStatusDto, description, messageDTO);
        
	        
	    }
	 
	 public void connectAndSubscribe() throws Exception {
		 if (client != null && client.isOpen()) {
			 regProcLogger.info("WebSocket already connected");
	            return;
	        }

	        URI uri = new URI(wsUrl);
	        Draft_6455 draft = new Draft_6455(Collections.emptyList(),
	                Collections.singletonList(new Protocol("graphql-transport-ws")));

	        client = new WebSocketClient(uri, draft) {
	            @Override
	            public void onOpen(ServerHandshake handshake) {
	            	regProcLogger.info("✅ Connected to GraphQL WS at : {}", LocalDateTime.now());
	                connected.set(true);
	                sendConnectionInit();
	            }

	            @Override
	            public void onMessage(String message) {
	                try {
	                    JsonObject msg = new JsonParser().parse(message).getAsJsonObject();
	                    String type = msg.has("type") ? msg.get("type").getAsString() : "";

	                    if ("connection_ack".equals(type)) {
	                    	regProcLogger.info("📡 connection_ack received - subscribing...");
	                        sendSubscribe();
	                    } else if ("next".equals(type)) {
	                    	regProcLogger.info("🔔 subscription event:");
	                    	regProcLogger.info("Message: {}",pretty(message));
	                        handleSubscriptionPayload(msg);
	                    } else if ("complete".equals(type)) {
	                    	regProcLogger.info("✅ subscription complete");
	                    } else {
	                    	regProcLogger.info("[WS MESSAGE] : {}", message);
	                    }
	                } catch (Exception e) {
	                	regProcLogger.error("Error parsing WS message: {}", e.getMessage());
	                }
	            }

	            @Override
	            public void onClose(int code, String reason, boolean remote) {
	                connected.set(false);
	                regProcLogger.info("❌ WebSocket closed: " + reason + " (code=" + code + ")");
	            }

	            @Override
	            public void onError(Exception ex) {
	            	regProcLogger.info("WebSocket error: {}", ex.getMessage());
	            }

	        };

	        client.connectBlocking();
	}

	 
	public void handleIdentifyPersonGraphQLResponse(IdentifyPersonGraphQLResponse response,
			InternalRegistrationStatusDto registrationStatusDto, LogDescription description, MessageDTO object) throws JsonMappingException, com.fasterxml.jackson.core.JsonProcessingException {
		IdentifyPersonGraphQLResponse.TransactionStatus transactionStatus = response.getTransactionStatus();
		Map<String, String> attributes = new HashMap<>();
		boolean isTransactionSuccessful = true;
		try {
			if (transactionStatus.getTransactionStatus().equalsIgnoreCase("Ok")) {
				String NIN = null;
				List<IdentifyPersonGraphQLResponse.Person> persons = response.getPerson();
				if (persons != null && !persons.isEmpty()) {
					if (persons.size() == 1) {
						regProcLogger.info("Single nin returned from legacy : {}", response.getRequestId());
						NIN = persons.get(0).getNationalId();
					} else {
						regProcLogger.error("Mulitple nins returned from legacy : {}", response.getRequestId());
						throw new ValidationFailedException(StatusUtil.LEGACY_DATA_FAILED.getMessage(),
								StatusUtil.LEGACY_DATA_FAILED.getCode());
					}
				} else {
					regProcLogger.info("No  nins returned from legacy : {}", response.getRequestId());
				}
				// call ondemand migration.
				legacyDataVal.onDemandMigration(NIN, response, registrationStatusDto, description);
				object.setIsValid(Boolean.TRUE);
				object.setInternalError(Boolean.FALSE);
			} else if (transactionStatus.getTransactionStatus().equalsIgnoreCase("Error")) {
				regProcLogger.info("Transaction status is Error : {}", response.getRequestId());
				regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), response.getRequestId(),
						RegistrationStatusCode.FAILED.toString() + transactionStatus.getError().getCode()
								+ transactionStatus.getError().getMessage());
				throw new LegacyDataValidationException(transactionStatus.getError().getCode(),
						transactionStatus.getError().getMessage());
			}
		} catch (JsonProcessingException e) {
			isTransactionSuccessful = false;
			object.setInternalError(true);
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED,
					StatusUtil.JSON_PARSING_EXCEPTION, RegistrationExceptionTypeCode.PARSE_EXCEPTION, description,
					PlatformErrorMessages.RPR_SYS_JSON_PARSING_EXCEPTION, e);
			
		} catch (ApisResourceAccessException e) {
			isTransactionSuccessful = false;
			object.setInternalError(true);
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.PROCESSING,
					StatusUtil.API_RESOUCE_ACCESS_FAILED, RegistrationExceptionTypeCode.APIS_RESOURCE_ACCESS_EXCEPTION,
					description,
					PlatformErrorMessages.RPR_RGS_REGISTRATION_TABLE_NOT_ACCESSIBLE, e);
		}  catch (ValidationFailedException e) {
			object.setInternalError(Boolean.FALSE);
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.REJECTED, StatusUtil.LEGACY_DATA_FAILED,
					RegistrationExceptionTypeCode.PACKET_REJECTED, description,
					PlatformErrorMessages.RPR_LEGACY_DATA_FAILED, e);
			attributes.put("FAILURE_CODE", StatusUtil.LEGACY_DATA_FAILED.getCode());
			attributes.put("FAILURE_REASON", StatusUtil.LEGACY_DATA_FAILED.getMessage());
			object.setNotificationAttributes(attributes);
		} catch (LegacyDataValidationException e) {
			isTransactionSuccessful = false;
			object.setInternalError(true);
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.LEGACYERROR,
					StatusUtil.LEGACY_DATA_SYSTEM_FAILED, RegistrationExceptionTypeCode.LEGACY_FAILED, description,
					PlatformErrorMessages.RPR_LEGACY_DATA_FAILED, e);
		} catch (DataMigrationPacketCreationException e) {
			isTransactionSuccessful = false;
			object.setInternalError(true);
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.REJECTED,
					StatusUtil.LEGACY_DATA_MIGRATION_API_FAILED,
					RegistrationExceptionTypeCode.DATA_MIGRATION_PACKET_CREATION_EXCEPTION, description,
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
			registrationStatusService.updateRegistrationStatus(registrationStatusDto, moduleId, moduleName);
			updateAudit(description, isTransactionSuccessful, moduleId, moduleName, registrationStatusDto.getRegistrationId());
			
			legacyDataStage.sendMessage(object);
			
			
		}
	}
	
	
}
