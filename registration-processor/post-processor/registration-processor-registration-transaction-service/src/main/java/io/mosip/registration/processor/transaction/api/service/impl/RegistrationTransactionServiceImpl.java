package io.mosip.registration.processor.transaction.api.service.impl;

import io.mosip.registration.processor.core.spi.packetmanager.PacketInfoManager;
import io.mosip.registration.processor.core.packet.dto.Identity;
import io.mosip.registration.processor.packet.storage.dto.ApplicantInfoDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RegistrationTransactionServiceImpl {

    private final PacketInfoManager<Identity, ApplicantInfoDto> packetInfoService;

    @Autowired
    public RegistrationTransactionServiceImpl(
            PacketInfoManager<Identity, ApplicantInfoDto> packetInfoService) {
        this.packetInfoService = packetInfoService;
    }

    public List<String> getManualVerificationDetails(String rid) {
        return packetInfoService.getManualVerificationDetails(rid);
    }
}