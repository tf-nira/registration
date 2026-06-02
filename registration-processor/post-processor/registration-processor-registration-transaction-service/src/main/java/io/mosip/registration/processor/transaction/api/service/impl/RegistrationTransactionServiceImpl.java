package io.mosip.registration.processor.transaction.api.service.impl;

import io.mosip.registration.processor.packet.storage.entity.ManualVerificationEntity;
import io.mosip.registration.processor.packet.storage.service.impl.PacketInfoManagerImpl;
import io.mosip.registration.processor.transaction.api.service.RegistrationTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RegistrationTransactionServiceImpl implements RegistrationTransactionService {

    @Autowired
    private PacketInfoManagerImpl packetInfoService;

    @Override
    public List<String> getManualVerificationDetails(String rid) {

        List<ManualVerificationEntity> list = packetInfoService.getManualVerification(rid);
        List<String> result = new ArrayList<>();

        if (list != null && !list.isEmpty()) {
            result = list.stream()
                    .filter(e -> e.getId() != null && e.getId().getMatchedRefType() != null)
                    .map(this::mapToResponse)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            result.add("No records found for the given Application ID.");
        }
        return result;
    }

    private String mapToResponse(ManualVerificationEntity e) {

        String type = e.getId().getMatchedRefType();
        if ("rid".equalsIgnoreCase(type)) {
            return e.getId().getMatchedRefId();
        }
        else if ("NIN".equalsIgnoreCase(type)) {
            String trnType = e.getTrnTypCode();
            switch (trnType.toUpperCase()) {
                case "INTRODUCER_VALIDATION_FAILURE":
                    return "Biometric Authentication failed for Introducer";
                case "BIO_AUTH_FAILURE":
                    return "Biometric Authentication failed for applicant";
                default:
                    return "Manual verification failed due to " + trnType;
            }
        }
        return null;
    }
}
