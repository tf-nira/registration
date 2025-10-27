package io.mosip.registration.processor.securezone.notification.controller;

import io.mosip.registration.processor.core.http.ResponseWrapper;
import io.mosip.registration.processor.securezone.notification.dto.ResponseDTO;
import io.mosip.registration.processor.securezone.notification.dto.StatusRequestDTO;
import io.mosip.registration.processor.securezone.notification.service.ApplicationService;
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

    @PostMapping("/prioritize")
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
