package io.mosip.registration.processor.paymentvalidator.stage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.packet.storage.utils.PacketManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.processor.core.abstractverticle.MessageBusAddress;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.abstractverticle.MosipEventBus;
import io.mosip.registration.processor.core.abstractverticle.MosipRouter;
import io.mosip.registration.processor.core.abstractverticle.MosipVerticleAPIManager;
import io.mosip.registration.processor.core.code.ApiName;
import io.mosip.registration.processor.core.code.EventId;
import io.mosip.registration.processor.core.code.EventName;
import io.mosip.registration.processor.core.code.EventType;
import io.mosip.registration.processor.core.code.ModuleName;
import io.mosip.registration.processor.core.code.RegistrationExceptionTypeCode;
import io.mosip.registration.processor.core.code.RegistrationTransactionStatusCode;
import io.mosip.registration.processor.core.code.RegistrationTransactionTypeCode;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.exception.util.PlatformSuccessMessages;
import io.mosip.registration.processor.core.http.ResponseWrapper;
import io.mosip.registration.processor.core.logger.LogDescription;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.core.status.util.StatusUtil;
import io.mosip.registration.processor.core.status.util.TrimExceptionMessage;
import io.mosip.registration.processor.core.util.RegistrationExceptionMapperUtil;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.paymentvalidator.constants.PrnStatusCode;
import io.mosip.registration.processor.paymentvalidator.dto.ConsumePrnRequestDTO;
import io.mosip.registration.processor.paymentvalidator.dto.IsPrnRegInLogsRequestDTO;
import io.mosip.registration.processor.paymentvalidator.dto.PrnStatusRequestDTO;
import io.mosip.registration.processor.paymentvalidator.dto.PrnStatusResponseDTO;
import io.mosip.registration.processor.paymentvalidator.dto.PrnStatusResponseDataDTO;
import io.mosip.registration.processor.rest.client.audit.builder.AuditLogRequestBuilder;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.service.RegistrationStatusService;

/**
 * Payment Validation Stage for verifying payment registration numbers
 * 
 * 
 * @author Ibrahim Nkambo
 */

@ComponentScan(basePackages = { "${mosip.auth.adapter.impl.basepackage}",
		"io.mosip.registration.processor.rest.client.config", "io.mosip.registration.processor.core.kernel.beans",
		"io.mosip.registration.processor.core.config", "io.mosip.registration.processor.packet.storage.config",
		"io.mosip.registrationprocessor.stages.config",
		"io.mosip.registration.processor.status.config",
		"io.mosip.registration.processor.paymentvalidator.config",
		"io.mosip.registration.processor.paymentvalidator.service" })
public class PaymentValidatorStage extends MosipVerticleAPIManager {

	private static final String STAGE_PROPERTY_PREFIX = "mosip.regproc.paymentvalidator.";
	private static Logger regProcLogger = RegProcessorLogger.getLogger(PaymentValidatorStage.class);

	/** The mosip event bus. */
	private MosipEventBus mosipEventBus;

	/**
	 * After this time intervel, message should be considered as expired (In
	 * seconds).
	 */
	@Value("${mosip.regproc.paymentvalidator.message.expiry-time-limit}")
	private Long messageExpiryTimeLimit;

	/** The cluster manager url. */
	@Value("${vertx.cluster.configuration}")
	private String clusterManagerUrl;

	/** worker pool size. */
	@Value("${worker.pool.size}")
	private Integer workerPoolSize;

	@Value("${nira.payment.gateway.statusCode}")
	private String statusCode;

	/** Mosip router for APIs */
	@Autowired
	MosipRouter router;

	@Autowired
    private RegistrationProcessorRestClientService<Object> restApi;

	/** The registration status service. */
	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

	@Autowired
	private PacketManagerService packetManagerService;
	
	@Value("${taxhead.replace.code}")
	private String taxheadReplaceCode;
	
	@Value("${taxhead.replace.amount}")
	private String taxheadReplaceAmount;
	
	@Value("${taxhead.change.code}")
	private String taxheadChangeCode;
	
	@Value("${taxhead.change.amount}")
	private String taxheadChangeAmount;
	
	@Value("${taxhead.correction_errors.code}")
	private String taxheadCorrectionsCode;
	
	@Value("${taxhead.correction_errors.amount}")
	private String taxheadCorrectionsAmount;
	
	@Value("${taxhead.replace_defaced.code}")
	private String taxheadDefacedCode;
	
	@Value("${taxhead.replace_defaced.amount}")
	private String taxheadDefacedAmount;
	//alien
	@Value("${taxhead.new.alien}")
	private String taxheadnewalien;
	@Value("${amount.new.alien}")
	private String amountnewalien;

	@Value("${taxhead.renewal.alien}")
	private String taxheadrenewalalien;
	@Value("${amount.renewal.alien}")
	private String amountrenewalalien;

	@Value("${taxhead.lost.alien}")
	private String taxheadlostalien;
	@Value("${amount.lost.alien}")
	private String amountlostalien;

	@Value("${taxhead.damaged.alien}")
	private String taxheaddamagedalien;
	@Value("${amount.damaged.alien}")
	private String amountdamagedalien;

	
	private static final String USER = "MOSIP_SYSTEM";
	
	@Autowired
	private AuditLogRequestBuilder auditLogRequestBuilder;
	
	private TrimExceptionMessage trimExpMessage = new TrimExceptionMessage();
	
	@Autowired
	RegistrationExceptionMapperUtil registrationStatusMapperUtil;
	
	@Autowired
	private Utilities utilities;

	@Override
	public MessageDTO process(MessageDTO object) {
		TrimExceptionMessage trimeExpMessage = new TrimExceptionMessage();
		object.setIsValid(Boolean.FALSE);
		object.setInternalError(Boolean.FALSE);
		object.setMessageBusAddress(MessageBusAddress.PAYMENT_VALIDATOR_BUS_IN);
		LogDescription description = new LogDescription();
		ObjectMapper objectMapper = new ObjectMapper();
		
		boolean isTransactionSuccessful = false;

		String regId = object.getRid();
		String regType = object.getReg_type();
		InternalRegistrationStatusDto registrationStatusDto = null;
		
		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
				regId, "PaymentValidatorStage::process()::entry");

		try {

			Object citizenshipType = packetManagerService.getField(regId, MappingJsonConstants.APPLICANT_CITIZENSHIPTYPE, regType, ProviderStageName.PAYMENT_VALIDATOR);
			Object replacementType = packetManagerService.getField(regId, MappingJsonConstants.REPLACEMENT_TYPE, regType, ProviderStageName.PAYMENT_VALIDATOR);
			String citizenshipTypePacket = null;
			String replacementTypePacket = null;
			if(citizenshipType != null && regType.equalsIgnoreCase("LOST")) {
				List<Map<String, String>> citizenshipTypeList = objectMapper.readValue(
						citizenshipType.toString(), new TypeReference<>() {});
				Optional<String> citizenshipTypeOpt = citizenshipTypeList.stream().findFirst().map(map -> map.get("value"));
				 citizenshipTypePacket = citizenshipTypeOpt.get();
			}
			else if(replacementType !=null) {
				List<Map<String, String>> replacementTypeList = objectMapper.readValue(
						replacementType.toString(), new TypeReference<>() {
						});
				// Extract values if lists are non-empty
				Optional<String> replacementTypeListOpt = replacementTypeList.stream().findFirst().map(map -> map.get("value"));
				 replacementTypePacket = replacementTypeListOpt.get();
			}


			registrationStatusDto = registrationStatusService.getRegistrationStatus(regId, object.getReg_type(),
					object.getIteration(), object.getWorkflowInstanceId());
			registrationStatusDto
					.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PAYMENT_VALIDATION.toString());
			registrationStatusDto.setRegistrationStageName(getStageName());

			String prnNum = utilities.getPacketManagerService().getField(regId, "PRNId", object.getReg_type(),
					ProviderStageName.PAYMENT_VALIDATOR);

			PrnStatusResponseDataDTO dataResponse = checkPrnStatus(prnNum);
			//PrnStatusResponseDataDTO dataResponse = prnStatusResponseMap.getData();
			
			if (dataResponse != null) {
				if (!statusCode.equalsIgnoreCase(dataResponse.getStatusCode())) {
					Map<String, String> notificationAttributes = new HashMap<>();
					notificationAttributes.put("FAILURE_REASON", StatusUtil.PAYMENT_VALIDATION_FAILED.getMessage() + "-" + 
							PlatformErrorMessages.RPR_PYVS_PRN_NOT_PAID.getMessage());
					object.setNotificationAttributes(notificationAttributes);
					
					regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString(), null,
							PlatformErrorMessages.RPR_PYVS_PRN_NOT_PAID.name());
					object.setIsValid(Boolean.FALSE);
					isTransactionSuccessful = false;
					description.setMessage(PlatformErrorMessages.RPR_PYVS_PRN_NOT_PAID.getMessage());
					description.setCode(PlatformErrorMessages.RPR_PYVS_PRN_NOT_PAID.getCode());

					registrationStatusDto.setStatusComment(
							StatusUtil.PAYMENT_VALIDATION_FAILED.getMessage());
					registrationStatusDto.setSubStatusCode(StatusUtil.PAYMENT_VALIDATION_FAILED.getCode());
					registrationStatusDto
							.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.REPROCESS.toString());
					registrationStatusDto
							.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PAYMENT_VALIDATION.toString());
					
				} else {
					regProcLogger.info("In Registration Processor - Payment Validator - Payment status check - passed");
					if (!validateTaxHeadAndRegType(dataResponse, regType,citizenshipTypePacket,replacementTypePacket)) {
						object.setIsValid(Boolean.FALSE);
						
						Map<String, String> notificationAttributes = new HashMap<>();
						notificationAttributes.put("FAILURE_REASON", StatusUtil.PAYMENT_VALIDATION_FAILED.getMessage() + "-" + 
								PlatformErrorMessages.RPR_PYVS_PRN_NOT_VALID_FOR_USECASE.getMessage());
						object.setNotificationAttributes(notificationAttributes);
						
						regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
								LoggerFileConstant.REGISTRATIONID.toString(), null,
								PlatformErrorMessages.RPR_PYVS_PRN_NOT_VALID_FOR_USECASE.name());
						object.setIsValid(Boolean.FALSE);
						isTransactionSuccessful = false;
						description.setMessage(PlatformErrorMessages.RPR_PYVS_PRN_NOT_VALID_FOR_USECASE.getMessage());
						description.setCode(PlatformErrorMessages.RPR_PYVS_PRN_NOT_VALID_FOR_USECASE.getCode());

						registrationStatusDto.setStatusComment(
								StatusUtil.PAYMENT_VALIDATION_FAILED.getMessage());
						registrationStatusDto.setSubStatusCode(StatusUtil.PAYMENT_VALIDATION_FAILED.getCode());
						registrationStatusDto
								.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.REJECTED.toString());
						registrationStatusDto
								.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PAYMENT_VALIDATION.toString());
					} else {
						regProcLogger.info("In Registration Processor - Payment Validator - PRN valid for the usecase");
						
						if(checkTranscLogs(prnNum, regId)) {
							/* Check for re-processing of packet */
							if(!registrationStatusDto.getStatusCode().equals("PROCESSED")
									&& !registrationStatusDto.getStatusCode().equals("PROCESSING")) {
								object.setIsValid(Boolean.TRUE);
								regProcLogger.info(
										"In Registration Processor - Payment Validator - PRN consumption success. Send to next stage.");
							}
							else {
								Map<String, String> notificationAttributes = new HashMap<>();
								notificationAttributes.put("FAILURE_REASON", StatusUtil.PAYMENT_VALIDATION_FAILED.getMessage() + "-" + 
										PlatformErrorMessages.RPR_PYVS_PRN_ALREADY_USED.getMessage());
								object.setNotificationAttributes(notificationAttributes);
								
								regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
										LoggerFileConstant.REGISTRATIONID.toString(), null,
										PlatformErrorMessages.RPR_PYVS_PRN_ALREADY_USED.name());
								object.setIsValid(Boolean.FALSE);
								isTransactionSuccessful = false;
								description.setMessage(PlatformErrorMessages.RPR_PYVS_PRN_ALREADY_USED.getMessage());
								description.setCode(PlatformErrorMessages.RPR_PYVS_PRN_ALREADY_USED.getCode());

								registrationStatusDto.setStatusComment(
										StatusUtil.PAYMENT_VALIDATION_FAILED.getMessage());
								registrationStatusDto.setSubStatusCode(StatusUtil.PAYMENT_VALIDATION_FAILED.getCode());
								registrationStatusDto
										.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.REJECTED.toString());
								registrationStatusDto
										.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PAYMENT_VALIDATION.toString());
								
							}
						}else {
							
							regProcLogger.info(
									"In Registration Processor - Payment Validator - PRN consumption check - false");
							/* Add regId and PRN to consumption */ 
							ConsumePrnRequestDTO consumePrnRequestDTO = new ConsumePrnRequestDTO();
							consumePrnRequestDTO.setPrn(prnNum);
							consumePrnRequestDTO.setRegId(regId);

							if (consumePrn(consumePrnRequestDTO)) {
								regProcLogger.info(
										"In Registration Processor - Payment Validator - PRN consumption success. Send to next stage.");
								isTransactionSuccessful = true;
							} else {
								Map<String, String> notificationAttributes = new HashMap<>();
								notificationAttributes.put("FAILURE_REASON", StatusUtil.PAYMENT_VALIDATION_FAILED.getMessage() + "-" + 
										PlatformErrorMessages.RPR_PYVS_CONSUMPTION_FAILED.getMessage());
								object.setNotificationAttributes(notificationAttributes);
								
								regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
										LoggerFileConstant.REGISTRATIONID.toString(), null,
										PlatformErrorMessages.RPR_PYVS_CONSUMPTION_FAILED.name());
								object.setIsValid(Boolean.FALSE);
								isTransactionSuccessful = false;
								description.setMessage(PlatformErrorMessages.RPR_PYVS_CONSUMPTION_FAILED.getMessage());
								description.setCode(PlatformErrorMessages.RPR_PYVS_CONSUMPTION_FAILED.getCode());

								registrationStatusDto.setStatusComment(
										StatusUtil.PAYMENT_VALIDATION_FAILED.getMessage());
								registrationStatusDto.setSubStatusCode(StatusUtil.PAYMENT_VALIDATION_FAILED.getCode());
								registrationStatusDto
										.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.REJECTED.toString());
								registrationStatusDto
										.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PAYMENT_VALIDATION.toString());
							}
						}
					}
				}
			}
			else {
				Map<String, String> notificationAttributes = new HashMap<>();
				notificationAttributes.put("FAILURE_REASON", StatusUtil.PAYMENT_VALIDATION_FAILED.getMessage() + "-" + 
						PlatformErrorMessages.RPR_PYVS_INVALID_PRN.getMessage());
				object.setNotificationAttributes(notificationAttributes);
				
				regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), null,
						PlatformErrorMessages.RPR_PYVS_INVALID_PRN.name());
				object.setIsValid(Boolean.FALSE);
				isTransactionSuccessful = false;
				description.setMessage(PlatformErrorMessages.RPR_PYVS_INVALID_PRN.getMessage());
				description.setCode(PlatformErrorMessages.RPR_PYVS_INVALID_PRN.getCode());

				registrationStatusDto.setStatusComment(
						StatusUtil.PAYMENT_VALIDATION_FAILED.getMessage());
				registrationStatusDto.setSubStatusCode(StatusUtil.PAYMENT_VALIDATION_FAILED.getCode());
				registrationStatusDto
						.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.REJECTED.toString());
				registrationStatusDto
						.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PAYMENT_VALIDATION.toString());
			}
			
			if (isTransactionSuccessful) {
				object.setIsValid(Boolean.TRUE);
				description.setMessage(PlatformSuccessMessages.RPR_PAYMENT_VALIDATOR_STAGE_SUCCESS.getMessage());
				description.setCode(PlatformSuccessMessages.RPR_PAYMENT_VALIDATOR_STAGE_SUCCESS.getCode());
				registrationStatusDto.setStatusComment(
						trimeExpMessage.trimExceptionMessage(StatusUtil.PAYMENT_VALIDATION_SUCCESS.getMessage()));
				registrationStatusDto.setSubStatusCode(StatusUtil.PAYMENT_VALIDATION_SUCCESS.getCode());
				registrationStatusDto
						.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.SUCCESS.toString());
				registrationStatusDto
						.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PAYMENT_VALIDATION.toString());

				regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), regId, "PaymentValidationStage::process()::exit");
			}
		} catch (ApisResourceAccessException e) {
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					regId, PlatformErrorMessages.RPR_PYVS_FAILED + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.REPROCESS, StatusUtil.API_RESOUCE_ACCESS_FAILED, RegistrationExceptionTypeCode.APIS_RESOURCE_ACCESS_EXCEPTION, description, PlatformErrorMessages.RPR_PYVS_FAILED, e);
		} catch (Exception e) {
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					regId, PlatformErrorMessages.RPR_PYVS_FAILED + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			updateDTOsAndLogError(registrationStatusDto, RegistrationStatusCode.FAILED, StatusUtil.UNKNOWN_EXCEPTION_OCCURED, RegistrationExceptionTypeCode.EXCEPTION, description, PlatformErrorMessages.RPR_PYVS_FAILED, e);
			
		} finally {
	        if (object.getInternalError()) {
	            int retryCount = registrationStatusDto.getRetryCount() != null ? registrationStatusDto.getRetryCount() + 1 : 1;
	            registrationStatusDto.setRetryCount(retryCount);
	            updateErrorFlags(registrationStatusDto, object);
	        }
	        registrationStatusDto.setUpdatedBy(USER);
	        String moduleId = description.getCode();
	        String moduleName = ModuleName.PAYMENT_VALIDATOR.toString();
	        registrationStatusService.updateRegistrationStatus(registrationStatusDto, moduleId, moduleName);
	        updateAudit(description, isTransactionSuccessful, moduleId, moduleName, regId);
	    }	

		return object;
	}
	
	private void updateAudit(LogDescription description, boolean isTransactionSuccessful, String moduleId, String moduleName, String registrationId) {
	    String eventId = isTransactionSuccessful ? EventId.RPR_402.toString() : EventId.RPR_405.toString();
	    String eventName = isTransactionSuccessful ? EventName.UPDATE.toString() : EventName.EXCEPTION.toString();
	    String eventType = isTransactionSuccessful ? EventType.BUSINESS.toString() : EventType.SYSTEM.toString();

	    auditLogRequestBuilder.createAuditRequestBuilder(description.getMessage(), eventId, eventName, eventType, moduleId, moduleName, registrationId);
	}

	private void updateErrorFlags(InternalRegistrationStatusDto registrationStatusDto, MessageDTO object) {
	    object.setInternalError(true);
	    if (registrationStatusDto.getLatestTransactionStatusCode().equalsIgnoreCase(RegistrationTransactionStatusCode.REPROCESS.toString())) {
	        object.setIsValid(true);
	    } else {
	        object.setIsValid(false);
	    }
	}

	private void updateDTOsAndLogError(InternalRegistrationStatusDto registrationStatusDto, RegistrationStatusCode registrationStatusCode, StatusUtil statusUtil, RegistrationExceptionTypeCode registrationExceptionTypeCode, LogDescription description, PlatformErrorMessages platformErrorMessages, Exception e) {
	    registrationStatusDto.setStatusCode(registrationStatusCode.toString());
	    registrationStatusDto.setStatusComment(trimExpMessage.trimExceptionMessage(statusUtil.getMessage() + e.getMessage()));
	    registrationStatusDto.setSubStatusCode(statusUtil.getCode());
	    registrationStatusDto.setLatestTransactionStatusCode(registrationStatusMapperUtil.getStatusCode(registrationExceptionTypeCode));
	    description.setMessage(platformErrorMessages.getMessage());
	    description.setCode(platformErrorMessages.getCode());
	    regProcLogger.error("Error in process for registration id {} {} {} {} {}", registrationStatusDto.getRegistrationId(), description.getCode(), platformErrorMessages.getMessage(), e.getMessage(), ExceptionUtils.getStackTrace(e));
	}

	@Override
	public void deployVerticle() {
		mosipEventBus = this.getEventBus(this, clusterManagerUrl, workerPoolSize);
		this.consumeAndSend(mosipEventBus, MessageBusAddress.PAYMENT_VALIDATOR_BUS_IN,
				MessageBusAddress.PAYMENT_VALIDATOR_BUS_OUT, messageExpiryTimeLimit);

		//process(new MessageDTO());
	}

	@Override
	protected String getPropertyPrefix() {
		return STAGE_PROPERTY_PREFIX;
	}

	@Override
	public void start() {
		router.setRoute(this.postUrl(getVertx(), MessageBusAddress.PAYMENT_VALIDATOR_BUS_IN,
				MessageBusAddress.PAYMENT_VALIDATOR_BUS_OUT));
		this.createServer(router.getRouter(), getPort());
	}
	/**
	 * This method calls an external API to check the transaction logs if PRN and RegId is present
	 * 
	 * 
	 * @param prn
	 * @param regId
	 * @return status
	 * @throws ApisResourceAccessException 
	 */
	@SuppressWarnings("unchecked")
	private boolean checkTranscLogs(String prn, String regId) throws ApisResourceAccessException {
		boolean isPresentInLogs = false;

		IsPrnRegInLogsRequestDTO isPrnRegInLogsRequestDTO = new IsPrnRegInLogsRequestDTO();
		isPrnRegInLogsRequestDTO.setPrn(prn);
		isPrnRegInLogsRequestDTO.setRegId(regId);

		HashMap<String, Boolean> responseMap = null;
		ResponseWrapper<?> response = (ResponseWrapper<?>) restApi.postApi(ApiName.CHECKTRANSLOGS, "", "", isPrnRegInLogsRequestDTO,
				ResponseWrapper.class);

		if (response.getErrors() != null) {
			isPresentInLogs = true;
		}
		else {
			responseMap = (HashMap<String, Boolean>) response.getResponse();
			if (responseMap != null && responseMap.get("presentInLogs") == true) {
				isPresentInLogs = true;
			}
		}
		
		return isPresentInLogs;

	}

	/**
	 * This method calls an external API to consume a PRN
	 * 
	 * @param consumePrnRequestDTO
	 * @return consumption status
	 * @throws ApisResourceAccessException 
	 */
	@SuppressWarnings("unchecked")
	private boolean consumePrn(ConsumePrnRequestDTO consumePrnRequestDTO) throws ApisResourceAccessException {
		HashMap<String, Boolean> responseMap = null;
		ResponseWrapper<?> response = (ResponseWrapper<?>) restApi.postApi(ApiName.CONSUMEPRN, "", "", consumePrnRequestDTO,
				ResponseWrapper.class);
		
		if (response != null && response.getResponse() != null) {
			responseMap = (HashMap<String, Boolean>) response.getResponse();
			return responseMap.get("consumedSucess");
		}

		return false;
	}
	
	/**
	 * This method calls an external API to check for the status of a PRN 
	 * 
	 * 
	 * @param prn
	 * @return PrnStatusResponseDTO
	 * @throws ApisResourceAccessException 
	 */
	private PrnStatusResponseDataDTO checkPrnStatus(String prn) throws ApisResourceAccessException {
		PrnStatusRequestDTO prnStatusRequestDTO = new PrnStatusRequestDTO();
		prnStatusRequestDTO.setPRN(prn);
		Object rawResponse = restApi.postApi(ApiName.GETPRNSTATUS, "", "", prnStatusRequestDTO, Object.class);
		Map<String, Object> responseMap = (Map<String, Object>) rawResponse;
		if (responseMap.get("errors")!= null) {
			List<Map<String, Object>> errors = (List<Map<String, Object>>) responseMap.get("errors");
			if (!errors.isEmpty()) {
				String errorCode = (String) errors.get(0).get("errorCode");
				if ("NPG_UNKNOWN_EXCEPTION".equalsIgnoreCase(errorCode)
						|| "SERVICE_UNAVAILABLE".equalsIgnoreCase(errorCode)
				        || "404".equalsIgnoreCase(errorCode)
                        || "NOT_FOUND".equalsIgnoreCase(errorCode)
				        || "NPG-CHECK-PRN-STATUS-001".equalsIgnoreCase(errorCode)) {
					throw new ApisResourceAccessException("External payment system unavailable: " + errorCode);
				}
			}
		}
		Map<String, Object> innerResponseMap = (Map<String, Object>) responseMap.get("response");
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // Ignore unknown fields
		PrnStatusResponseDataDTO dataDTO = mapper.convertValue(innerResponseMap, PrnStatusResponseDataDTO.class);
		return dataDTO;
	}

	/**
	 * This method validates the PRN taxhead against the registration type i.e. LOST, UPDATE
	 * 
	 * @param response
	 * @param regType
	 * @return status
	 */
	private boolean validateTaxHeadAndRegType(PrnStatusResponseDataDTO response, String regType ,String citizenshipTypePacket , String replacementTypePacket) {
		String paidFor = "None";
		if("Alien New Registration".equalsIgnoreCase(citizenshipTypePacket)){
			paidFor = "NEWAID";
		}
		else if("Renewal of Alien".equalsIgnoreCase(citizenshipTypePacket)){
			paidFor = "RENAID";
		}
		else if("Lost".equalsIgnoreCase(replacementTypePacket)){
			paidFor = "LOSTAID";
		}
		else if ("Damaged/ Defaced".equalsIgnoreCase(replacementTypePacket)){
			paidFor = "DMGAID";
		}

		if(regType.equalsIgnoreCase(response.getProcessFlow())) {
			if(response.getTaxHeadCode().equalsIgnoreCase(taxheadChangeCode)){
				if(response.getAmountPaid().equals(taxheadChangeAmount)) {
					return true;
				}
			}
			else if(response.getTaxHeadCode().equalsIgnoreCase(taxheadCorrectionsCode)){
				if(response.getAmountPaid().equals(taxheadCorrectionsAmount)) {
					return true;
				}
			}
			else if(response.getTaxHeadCode().equalsIgnoreCase(taxheadReplaceCode)){
				if(response.getAmountPaid().equals(taxheadReplaceAmount)) {
					return true;
				}
			}
			else if(response.getTaxHeadCode().equalsIgnoreCase(taxheadDefacedCode)){
				if(response.getAmountPaid().equals(taxheadDefacedAmount)) {
					return true;
				}
			}
			//
			else if(response.getTaxHeadCode().equalsIgnoreCase(taxheadnewalien)){
				if(response.getSubServiceTypePaidFor().equalsIgnoreCase(paidFor)) {
					return true;
				}
			}
			else if(response.getTaxHeadCode().equalsIgnoreCase(taxheadrenewalalien)){
				if(response.getSubServiceTypePaidFor().equalsIgnoreCase(paidFor)) {
					return true;
				}
			}
			else if(response.getTaxHeadCode().equalsIgnoreCase(taxheadlostalien)){
				if(response.getSubServiceTypePaidFor().equalsIgnoreCase(paidFor)) {
					return true;
				}
			}
			else if(response.getTaxHeadCode().equalsIgnoreCase(taxheaddamagedalien)){
				if(response.getSubServiceTypePaidFor().equalsIgnoreCase(paidFor)) {
					return true;
				}
			}
			else {
				return false;
			}
		}
		
		return false;
	}

}
