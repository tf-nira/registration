package io.mosip.registration.processor.transaction.api.controller;

import java.util.List;
import java.util.Objects;

import java.util.ArrayList;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;

import io.mosip.registration.processor.packet.storage.entity.ManualVerificationEntity;
import io.mosip.registration.processor.packet.storage.service.impl.PacketInfoManagerImpl;
import io.mosip.registration.processor.transaction.api.service.impl.RegistrationTransactionServiceImpl;
import io.mosip.registration.processor.core.http.ResponseWrapper;
import io.mosip.registration.processor.status.entity.RegistrationStatusEntity;
import io.mosip.registration.processor.status.repositary.RegistrationRepositary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.token.validation.exception.AccessDeniedException;
import io.mosip.registration.processor.core.token.validation.exception.InvalidTokenException;
import io.mosip.registration.processor.core.util.DigitalSignatureUtility;
import io.mosip.registration.processor.status.dto.RegistrationTransactionDto;
import io.mosip.registration.processor.status.dto.TransactionDto;
import io.mosip.registration.processor.status.exception.RegTransactionAppException;
import io.mosip.registration.processor.status.exception.TransactionTableNotAccessibleException;
import io.mosip.registration.processor.status.exception.TransactionsUnavailableException;
import io.mosip.registration.processor.status.service.TransactionService;
import io.mosip.registration.processor.status.sync.response.dto.RegTransactionResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * RegistrationTransactionController class to retreive transaction details
 * @author Jyoti Prakash Nayak
 *
 */
@RefreshScope
@RestController
@Tag(name = "Registration Status", description = "Registration Transaction Controller")
public class RegistrationTransactionController {
	
	@Autowired
	TransactionService<TransactionDto> transactionService;
	
	@Autowired
	private Environment env;

	@Value("${registration.processor.signature.isEnabled}")
	private Boolean isEnabled;
	
	@Autowired
	private DigitalSignatureUtility digitalSignatureUtility;
	
	@Autowired
	ObjectMapper objMp;

	@Autowired
	private RegistrationRepositary registrationRepositary;

	@Autowired
	private PacketInfoManagerImpl packetInfoService;

	@Autowired
	private RegistrationTransactionServiceImpl RegistrationTransactionService;

	private static final String INVALIDTOKENMESSAGE = "Authorization Token Not Available In The Header";
	private static final String REG_TRANSACTION_SERVICE_ID = "mosip.registration.processor.registration.transaction.id";
	private static final String REG_TRANSACTION_APPLICATION_VERSION = "mosip.registration.processor.transaction.version";
	private static final String DATETIME_PATTERN = "mosip.registration.processor.datetime.pattern";
	private static final String RESPONSE_SIGNATURE = "Response-Signature";
	
	private static Logger regProcLogger = RegProcessorLogger.getLogger(RegistrationTransactionController.class);

	
	/**
	 * get transaction details for the given registration id
	 * 
	 * @param rid registration id
	 * @param request servlet request
	 * @return list of RegTransactionResponseDTOs 
	 * @throws Exception
	 */
	@PreAuthorize("hasAnyRole(@authorizedTransactionRoles.getGetsearchrid())")
	//@PreAuthorize("hasAnyRole('REGISTRATION_PROCESSOR','REGISTRATION_ADMIN')")
	@GetMapping(path = "/search/{rid}")
	@Operation(summary = "Get the transaction entity/entities", description = "Get the transaction entity/entities", tags = { "Registration Status" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Transaction Entity/Entities successfully fetched"),
			@ApiResponse(responseCode = "400", description = "Unable to fetch Transaction Entity/Entities" ,content = @Content(schema = @Schema(hidden = true))),
			@ApiResponse(responseCode = "401", description = "Unauthorized" ,content = @Content(schema = @Schema(hidden = true))),
			@ApiResponse(responseCode = "403", description = "Forbidden" ,content = @Content(schema = @Schema(hidden = true))),
			@ApiResponse(responseCode = "404", description = "Not Found" ,content = @Content(schema = @Schema(hidden = true)))})
	public ResponseEntity<RegTransactionResponseDTO> getTransactionsbyRid(@PathVariable("rid") String rid,
			HttpServletRequest request) throws Exception {
		List<RegistrationTransactionDto> dtoList;
		HttpHeaders headers = new HttpHeaders();
		try {
			dtoList = transactionService.getTransactionByRegId(rid);
			RegTransactionResponseDTO responseDTO=buildRegistrationTransactionResponse(dtoList);
			if (isEnabled) {		 
				headers.add(RESPONSE_SIGNATURE,
						digitalSignatureUtility.getDigitalSignature(buildSignatureRegistrationTransactionResponse(responseDTO)));	
				return ResponseEntity.status(HttpStatus.OK).headers(headers).body(responseDTO);
			}
				return ResponseEntity.status(HttpStatus.OK).body(responseDTO);
		}catch (Exception e) {
			if( e instanceof InvalidTokenException |e instanceof AccessDeniedException | e instanceof RegTransactionAppException
				| e instanceof TransactionsUnavailableException | e instanceof TransactionTableNotAccessibleException | e instanceof JsonProcessingException ) {
				throw e;
			}
			else {
				throw new RegTransactionAppException(PlatformErrorMessages.RPR_RTS_UNKNOWN_EXCEPTION.getCode(), 
						PlatformErrorMessages.RPR_RTS_UNKNOWN_EXCEPTION.getMessage()+" -->"+e.getMessage());
			}
		}
	}

	@PreAuthorize("hasAnyRole(@authorizedTransactionRoles.getGetsearchrid())")
	@PostMapping("/prioritize/{rid}")
	@Operation(summary = "Get the status entity", description = "Get the rid status ", tags = { "Registration Status" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Transaction Entity/Entities successfully fetched"),
			@ApiResponse(responseCode = "400", description = "Unable to fetch Transaction Entity/Entities", content = @Content(schema = @Schema(hidden = true))),
			@ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(hidden = true))),
			@ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(hidden = true))),
			@ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(hidden = true)))
	})
	public ResponseWrapper<String> packetResumable(@PathVariable("rid") String rid) {

		ResponseWrapper responseWrapper=new ResponseWrapper<>();
		String response=null;
		try {
			response = processing(rid);
			responseWrapper.setResponse(response);

		} catch (Exception exc) {
			throw new RuntimeException("Unexpected error occurred: " + exc.getMessage(), exc);
		}

		return responseWrapper;
	}

	@PreAuthorize("hasAnyRole(@authorizedTransactionRoles.getGetsearchrid())")
	@GetMapping(path = "/manual-verification/{rid}")
	@Operation(summary = "Get Manual Verification details", description = "Fetch MA match records using registration ID",
			tags = { "Registration Status" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Transaction Entity/Entities successfully fetched"),
			@ApiResponse(responseCode = "400", description = "Unable to fetch Transaction Entity/Entities", content = @Content(schema = @Schema(hidden = true))),
			@ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(hidden = true))),
			@ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(hidden = true))),
			@ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(hidden = true)))
	})
	public ResponseEntity<List<String>> getManualVerification(
			@PathVariable("rid") String rid) {

		List<String> result = RegistrationTransactionService.getManualVerificationDetails(rid);
		return ResponseEntity.ok(result);
	}


	public String processing(String rid) {
		String response = "null";
		List<RegistrationStatusEntity> records = registrationRepositary.findByRegId(rid);

		if (records == null || records.isEmpty()) {
			response = "Application not present";
		} else {
			boolean updated = false;

			for (RegistrationStatusEntity recordEntity : records) {
				if (recordEntity != null &&
						"SecurezoneNotificationStage".equalsIgnoreCase(recordEntity.getRegistrationStageName())) {

					if ("PROCESSING".equalsIgnoreCase(recordEntity.getStatusCode()) &&
							"SUCCESS".equalsIgnoreCase(recordEntity.getLatestTransactionStatusCode())) {

						recordEntity.setStatusCode("RESUMABLE");
						registrationRepositary.save(recordEntity);
						response = "Successfully updated";
						updated = true;
						break;
					}
				}
			}

			if (!updated ) {
				response = "Application cant be resumed";
			}
		}

		return response;

	}


	/**
	 * build the registration transaction response
	 * @param dtoList registration transaction dtos
	 * @return registration transaction response
	 */
	private RegTransactionResponseDTO buildRegistrationTransactionResponse(List<RegistrationTransactionDto> dtoList) {
		RegTransactionResponseDTO regTransactionResponseDTO= new RegTransactionResponseDTO();
		if (Objects.isNull(regTransactionResponseDTO.getId())) {
			regTransactionResponseDTO.setId(env.getProperty(REG_TRANSACTION_SERVICE_ID));
		}
		regTransactionResponseDTO.setResponsetime(DateUtils.getUTCCurrentDateTimeString(env.getProperty(DATETIME_PATTERN)));
		regTransactionResponseDTO.setVersion(env.getProperty(REG_TRANSACTION_APPLICATION_VERSION));
		regTransactionResponseDTO.setErrors(null);
		regTransactionResponseDTO.setResponse(dtoList);
		return regTransactionResponseDTO;
	}

	/**
	 * convert registration transaction response dto to json string
	 * @param dto registration transaction response dto
	 * @return
	 * @throws JsonProcessingException 
	 */
	private String buildSignatureRegistrationTransactionResponse(RegTransactionResponseDTO dto) throws JsonProcessingException {

		try {
			return objMp.writeValueAsString(dto);
		} catch (JsonProcessingException e) {
			regProcLogger.error("Error while processing response ",e);
			throw e;
		}
		
		
	}
}
