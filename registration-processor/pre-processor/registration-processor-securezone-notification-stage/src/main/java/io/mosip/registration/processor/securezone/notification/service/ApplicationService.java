package io.mosip.registration.processor.securezone.notification.service;

import io.mosip.registration.processor.securezone.notification.dto.ResponseDTO;

public interface ApplicationService {
    ResponseDTO processing(String rid);
}
