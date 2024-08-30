package io.mosip.registration.processor.paymentvalidator.stage;

import java.io.IOException;
import java.util.HashMap;

import java.util.Objects;

import org.json.simple.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.processor.core.abstractverticle.MessageBusAddress;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.abstractverticle.MosipEventBus;
import io.mosip.registration.processor.core.abstractverticle.MosipRouter;
import io.mosip.registration.processor.core.abstractverticle.MosipVerticleAPIManager;
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
import io.mosip.registration.processor.core.status.util.StatusUtil;
import io.mosip.registration.processor.core.status.util.TrimExceptionMessage;
import io.mosip.registration.processor.core.util.RegistrationExceptionMapperUtil;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.paymentvalidator.constants.PrnStatusCode;
import io.mosip.registration.processor.paymentvalidator.constants.RegType;
import io.mosip.registration.processor.paymentvalidator.constants.TaxHeadCode;
import io.mosip.registration.processor.paymentvalidator.dto.ConsumePrnRequestDTO;
import io.mosip.registration.processor.paymentvalidator.dto.IsPrnRegInLogsRequestDTO;
import io.mosip.registration.processor.paymentvalidator.dto.PrnStatusResponseDTO;
import io.mosip.registration.processor.paymentvalidator.dto.PrnStatusResponseDataDTO;
import io.mosip.registration.processor.paymentvalidator.dto.PrnStatusRequestDTO;
import io.mosip.registration.processor.paymentvalidator.util.CustomizedRestApiClient;
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

	@Value("${gateway.payment.service.api.get-prn-status}")
	private String getPrnStatusApiUrl;

	@Value("${gateway.payment.service.api.check-if-prn-consumed}")
	private String checkPrnConsumptionApiUrl;

	@Value("${gateway.payment.service.api.consume-prn}")
	private String consumePrnApiUrl;

	@Value("${gateway.payment.service.api.check-logs}")
	private String checkLogsApiUrl;

	/** The mosip event bus. */
	private MosipEventBus mosipEventBus;

	/**
	 * After this time intervel, message should be considered as expired (In
	 * seconds).
	 */
	@Value("${mosip.regproc.paymentvalidator.message.expiry-time-limit}")
	private Long messageExpiryTimeLimit;

	private static final String DATETIME_PATTERN = "mosip.registration.processor.datetime.pattern";

	/** The cluster manager url. */
	@Value("${vertx.cluster.configuration}")
	private String clusterManagerUrl;

	/** worker pool size. */
	@Value("${worker.pool.size}")
	private Integer workerPoolSize;

	/** Mosip router for APIs */
	@Autowired
	MosipRouter router;

	@Autowired
	CustomizedRestApiClient restApiClient;

	private ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private Utilities utilities;

	@Autowired
	private Environment env;
	
	private static final String SEPERATOR = "::";

	/** The registration status service. */
	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;
	
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
	
	private static final String USER = "MOSIP_SYSTEM";
	
	@Autowired
	private AuditLogRequestBuilder auditLogRequestBuilder;
	
	private TrimExceptionMessage trimExpMessage = new TrimExceptionMessage();
	
	@Autowired
	RegistrationExceptionMapperUtil registrationStatusMapperUtil;

	@SuppressWarnings("null")
	@Override
	public MessageDTO process(MessageDTO object) {
		TrimExceptionMessage trimeExpMessage = new TrimExceptionMessage();
		object.setIsValid(Boolean.FALSE);
		object.setInternalError(Boolean.FALSE);
		object.setMessageBusAddress(MessageBusAddress.PAYMENT_VALIDATOR_BUS_IN);
		LogDescription description = new LogDescription();
		
		boolean isTransactionSuccessful = false;

		String regId = object.getRid();
		String regType = object.getReg_type();
		InternalRegistrationStatusDto registrationStatusDto = null;
		
		regProcLogger.info("In Registration Processor - Payment Validator - Entering payment validator stage");
		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
				regId, "PaymentValidatorStage::process()::entry");

		try {
			registrationStatusDto = registrationStatusService.getRegistrationStatus(regId, object.getReg_type(),
					object.getIteration(), object.getWorkflowInstanceId());
			registrationStatusDto
					.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PAYMENT_VALIDATION.toString());
			registrationStatusDto.setRegistrationStageName(getStageName());

			regProcLogger.info("In Registration Processor - Payment Validator - Extracting PRN from packet");
			String prnNum = utilities.getPacketManagerService().getField(regId, "PRN", object.getReg_type(),
					ProviderStageName.PAYMENT_VALIDATOR);

			/* Will change to NIN in new env version */
			regProcLogger.info("In Registration Processor - Payment Validator - Extracting NIN from packet");
			String nin = utilities.getPacketManagerService().getField(regId, "NIN", object.getReg_type(),
					ProviderStageName.PAYMENT_VALIDATOR);

			if (regType.equalsIgnoreCase(RegType.LOST_USECASE) || regType.equalsIgnoreCase(RegType.UPDATE_USECASE)) {

					PrnStatusResponseDTO prnStatusResponseMap = checkPrnStatus(prnNum);
					PrnStatusResponseDataDTO dataResponse = prnStatusResponseMap.getData();
					
					if (dataResponse != null) {
						try {
							/* will change to allow handles method using NIN */
							//JSONObject uinJson = utilities.retrieveIdrepoJson(uin);
							JSONObject uinJson = utilities.retrieveIdrepoJsonWithNIN(nin);
							//regProcLogger.info("Retreived NIN idrepo {}: " + uinJson.toString());
						
							if (Objects.isNull(uinJson)) {

								/* Send notification to applicant here */
								
								
								regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
										LoggerFileConstant.REGISTRATIONID.toString(), null,
										PlatformErrorMessages.RPR_PYVS_UIN_DOESNT_EXIST.name());
								object.setIsValid(Boolean.FALSE);
								isTransactionSuccessful = false;
								description.setMessage(PlatformErrorMessages.RPR_PYVS_UIN_DOESNT_EXIST.getMessage());
								description.setCode(PlatformErrorMessages.RPR_PYVS_UIN_DOESNT_EXIST.getCode());

								registrationStatusDto.setStatusComment(
										StatusUtil.PAYMENT_VALIDATION_FAILED.getMessage());
								registrationStatusDto.setSubStatusCode(StatusUtil.PAYMENT_VALIDATION_FAILED.getCode());
								registrationStatusDto
										.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.REJECTED.toString());
								registrationStatusDto
										.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PAYMENT_VALIDATION.toString());
								
								
							} else {
								regProcLogger.info("In Registration Processor - Payment Validator - UIN/NIN check - passed");
								/* can we wait for payment if the PRN isn't paid yet? */
								if (!dataResponse.getStatusCode()
										.equalsIgnoreCase(PrnStatusCode.PRN_STATUS_RECEIVED_CREDITED.getStatusCode())) {
									/* Send notification to applicant here */
									/* how to route packet to try for more time for update of status from payment gateway service */
									
									
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
									if (!validateTaxHeadAndRegType(dataResponse, regType)) {
										regProcLogger.info("In Registration Processor - Payment Validator - PRN not valid for the usecase");
										object.setIsValid(Boolean.FALSE);
										/* Send notification to applicant here */
										
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

												/* Send notification to applicant here */
												
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

											regProcLogger.info(
													"In Registration Processor - Payment Validator - Proceeding to consume PRN");
											
											
											try {
												if (consumePrn(consumePrnRequestDTO)) {
													//object.setIsValid(Boolean.TRUE);
													regProcLogger.info(
															"In Registration Processor - Payment Validator - PRN consumption success. Send to next stage.");
													isTransactionSuccessful = true;
													
												} else {
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
											catch (Exception e) {
												regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
														LoggerFileConstant.REGISTRATIONID.toString(), null,
														PlatformErrorMessages.RPR_PYVS_CONSUMPTION_FAILED.name());
												object.setIsValid(Boolean.FALSE);
												isTransactionSuccessful = false;
												description.setMessage(PlatformErrorMessages.RPR_PYVS_CONSUMPTION_FAILED.getMessage());
												description.setCode(PlatformErrorMessages.RPR_PYVS_CONSUMPTION_FAILED.getCode());

												registrationStatusDto.setStatusComment(
														StatusUtil.API_RESOUCE_ACCESS_FAILED.getMessage());
												registrationStatusDto.setSubStatusCode(StatusUtil.API_RESOUCE_ACCESS_FAILED.getCode());
												registrationStatusDto
														.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.REPROCESS.toString());
												registrationStatusDto
														.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PAYMENT_VALIDATION.toString());
												object.setInternalError(Boolean.TRUE);
											}
	
										}
										
										
									}

								}

							}

						} catch (Exception e) {							
							regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
									LoggerFileConstant.REGISTRATIONID.toString(), null,
									PlatformErrorMessages.RPR_PYVS_IDREPO_UIN_RETRIEVAL_FAILED.name());
							object.setIsValid(Boolean.FALSE);
							isTransactionSuccessful = false;
							description.setMessage(PlatformErrorMessages.RPR_PYVS_IDREPO_UIN_RETRIEVAL_FAILED.getMessage());
							description.setCode(PlatformErrorMessages.RPR_PYVS_IDREPO_UIN_RETRIEVAL_FAILED.getCode());

							registrationStatusDto.setStatusComment(
									StatusUtil.PAYMENT_VALIDATION_FAILED.getMessage());
							registrationStatusDto.setSubStatusCode(StatusUtil.PAYMENT_VALIDATION_FAILED.getCode());
							registrationStatusDto
									.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.REPROCESS.toString());
							registrationStatusDto
									.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PAYMENT_VALIDATION.toString());
							object.setInternalError(Boolean.TRUE);
						}
					}
					else {
						/* Send notification to applicant here */
						
						
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
						//registrationStatusDto.setRefId(refIds);
						object.setIsValid(Boolean.TRUE);
						description.setMessage(PlatformSuccessMessages.RPR_PAYMENT_VALIDATOR_STAGE_SUCCESS.getMessage());
						description.setCode(PlatformSuccessMessages.RPR_PAYMENT_VALIDATOR_STAGE_SUCCESS.getCode());
						registrationStatusDto.setStatusComment(
								trimeExpMessage.trimExceptionMessage(StatusUtil.PAYMENT_VALIDATION_SUCCESS.getMessage()));
						registrationStatusDto.setSubStatusCode(StatusUtil.PAYMENT_VALIDATION_SUCCESS.getCode());
						registrationStatusDto
								.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.PROCESSED.toString());
						registrationStatusDto
								.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PAYMENT_VALIDATION.toString());

						regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(),
								LoggerFileConstant.REGISTRATIONID.toString(), regId, "PaymentValidationStage::process()::exit");
					}
			

			}
			else {
				regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), null,
						PlatformErrorMessages.RPR_PYVS_WRONG_PROCESS.name());
				object.setIsValid(Boolean.FALSE);
				isTransactionSuccessful = false;
				description.setMessage(PlatformErrorMessages.RPR_PYVS_WRONG_PROCESS.getMessage());
				description.setCode(PlatformErrorMessages.RPR_PYVS_WRONG_PROCESS.getCode());

				registrationStatusDto.setStatusComment(
						StatusUtil.PAYMENT_VALIDATION_FAILED.getMessage());
				registrationStatusDto.setSubStatusCode(StatusUtil.PAYMENT_VALIDATION_FAILED.getCode());
				registrationStatusDto
						.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.FAILED.toString());
				registrationStatusDto
						.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PAYMENT_VALIDATION.toString());
			}
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
	 */
	@SuppressWarnings("unchecked")
	private boolean checkTranscLogs(String prn, String regId) {
		regProcLogger.info(
				"In Registration Processor - Payment Validator - Checking payment gateway service if PRN and Reg Id are present in transaction logs");

		boolean isPresentInLogs = false;

		IsPrnRegInLogsRequestDTO isPrnRegInLogsRequestDTO = new IsPrnRegInLogsRequestDTO();
		isPrnRegInLogsRequestDTO.setPrn(prn);
		isPrnRegInLogsRequestDTO.setRegId(regId);

		HashMap<String, Boolean> responseMap = null;
		ResponseWrapper<?> response = null;

		try {
			response = restApiClient.postApi(checkLogsApiUrl, MediaType.APPLICATION_JSON, isPrnRegInLogsRequestDTO,
					ResponseWrapper.class);
			
			if(response.getErrors()!=null) {
				isPresentInLogs = true;
			}
			else {
				responseMap = (HashMap<String, Boolean>) response.getResponse();
				if (responseMap != null && responseMap.get("presentInLogs")==true) {
					isPresentInLogs = true;
				}
			}	
		} catch (Exception e) {
			regProcLogger.error("Internal Error occured while contacting gateway service for PRN status. "
					+ ExceptionUtils.getStackTrace(e));
		}
		return isPresentInLogs;

	}

	/**
	 * This method calls an external API to consume a PRN
	 * 
	 * @param consumePrnRequestDTO
	 * @return consumption status
	 */
	@SuppressWarnings("unchecked")
	private boolean consumePrn(ConsumePrnRequestDTO consumePrnRequestDTO) {
		regProcLogger.info(
				"In Registration Processor - Payment Validator - Consuming of PRN and addition into transaction logs");
		HashMap<String, Boolean> responseMap = null;
		try {
			regProcLogger.info("Request {} :" + consumePrnRequestDTO.toString());
			ResponseWrapper<?> response = restApiClient.postApi(consumePrnApiUrl, MediaType.APPLICATION_JSON, consumePrnRequestDTO,
					ResponseWrapper.class);
			regProcLogger.info(
					"In Registration Processor - Payment Validator - response for consumeprn: " + response.toString());

			if (response != null && response.getResponse() != null) {
				responseMap = (HashMap<String, Boolean>) response.getResponse();
				
				return responseMap.get("consumedSucess");
			}

		} catch (Exception e) {
			regProcLogger.error("Internal Error occured contacting gateway service for PRN consumption. "
					+ ExceptionUtils.getStackTrace(e));
		}

		return false;

	}
	
	/**
	 * This method calls an external API to check for the status of a PRN 
	 * 
	 * 
	 * @param prn
	 * @return PrnStatusResponseDTO
	 */
	private PrnStatusResponseDTO checkPrnStatus(String prn) {
		regProcLogger.info(
				"In Registration Processor - Payment Validator - Checking payment gateway service for PRN status");

		PrnStatusRequestDTO prnStatusRequestDTO = new PrnStatusRequestDTO();
		prnStatusRequestDTO.setPRN(prn);
		PrnStatusResponseDTO response = null;

		try {
			response = restApiClient.postApi(getPrnStatusApiUrl, MediaType.APPLICATION_JSON, prnStatusRequestDTO,
					PrnStatusResponseDTO.class);
		} catch (Exception e) {
			regProcLogger.error("Internal Error occured while contacting gateway service for PRN status. "
					+ ExceptionUtils.getStackTrace(e));
		}
		return response;
	}
	
	/**
	 * This method validates the PRN taxhead against the registration type i.e. LOST, UPDATE
	 * 
	 * @param response
	 * @param regType
	 * @return status
	 */
	private boolean validateTaxHeadAndRegType(PrnStatusResponseDataDTO response, String regType) {
		
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
			else {
				return false;
			}
		}
		
		return false;
	}
	
	/**
	 * This method sends notification to applicant based on successful and failed processing of payment check
	 */
	private void sendNotification() {
		
	}

}
