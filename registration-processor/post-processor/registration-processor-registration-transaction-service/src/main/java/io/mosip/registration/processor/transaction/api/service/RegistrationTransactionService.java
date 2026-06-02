package io.mosip.registration.processor.transaction.api.service;

import java.util.List;

public interface RegistrationTransactionService {

    List<String> getManualVerificationDetails(String rid);

}
