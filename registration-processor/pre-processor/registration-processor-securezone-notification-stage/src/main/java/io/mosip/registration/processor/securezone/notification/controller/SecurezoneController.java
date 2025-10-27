package io.mosip.registration.processor.securezone.notification.controller;

import io.mosip.registration.processor.core.http.ResponseWrapper;
import io.mosip.registration.processor.securezone.notification.dto.ResponseDTO;
import io.mosip.registration.processor.securezone.notification.dto.StatusRequestDTO;
import io.mosip.registration.processor.securezone.notification.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/packet")
public class SecurezoneController {

    private final ApplicationService applicationService;

    public SecurezoneController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PreAuthorize("hasAnyRole(@authorizedTransactionRoles.getGetsearchrid())")
    @PostMapping("/prioritize")
    @Operation(summary = "Get the transaction entity/entities", description = "Get the transaction entity/entities", tags = { "Registration Status" })
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction Entity/Entities successfully fetched"),
            @ApiResponse(responseCode = "400", description = "Unable to fetch Transaction Entity/Entities", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(hidden = true)))
    })
    public ResponseWrapper<ResponseDTO> packetResumable(@RequestBody StatusRequestDTO statusRequest) {

        ResponseWrapper<ResponseDTO> responseWrapper = new ResponseWrapper<>();
        responseWrapper.setId("securezone");
        responseWrapper.setVersion("V1.0");

        try {
            ResponseDTO responseDTO = applicationService.processing(statusRequest.getRid());
            responseWrapper.setResponse(responseDTO);

        } catch (Exception exc) {
            // Use standard RuntimeException instead
            throw new RuntimeException("Unexpected error occurred: " + exc.getMessage(), exc);
        }

        return responseWrapper;
    }
}
