package io.mosip.registration.processor.securezone.notification.service.impl;

import io.mosip.registration.processor.securezone.notification.dto.ResponseDTO;
import io.mosip.registration.processor.securezone.notification.entity.Registration;
import io.mosip.registration.processor.securezone.notification.repository.RegistrationRepo;
import io.mosip.registration.processor.securezone.notification.service.ApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ApplicationServiceImpl implements ApplicationService {

    private final RegistrationRepo registrationRepo;

    @Autowired
    public ApplicationServiceImpl(RegistrationRepo registrationRepo) {
        this.registrationRepo = registrationRepo;
    }

    @Override
    public ResponseDTO processing(String rid) {

        ResponseDTO responseDTO = new ResponseDTO();

        Optional<Registration> optionalRegistration = registrationRepo.findById(rid);

        if (optionalRegistration.isEmpty()) {
            responseDTO.setResponse("Registration not found");
            return responseDTO;
        }

        Registration registration = optionalRegistration.get();

        if ("SecurezoneNotificationStage".equalsIgnoreCase(registration.getRegStageName())) {
            int updatedRows = registrationRepo.updateRegistration(registration.getRegId());
            if (updatedRows > 0) {
                responseDTO.setResponse("Registration found and updated successfully");
            } else {
                responseDTO.setResponse("Registration found but update failed");
            }
        } else {
            responseDTO.setResponse("Registration stage is not Securezone Stage");
        }

        return responseDTO;
    }
}
