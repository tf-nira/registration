package io.mosip.registration.processor.transaction.api.config;

import io.mosip.registration.processor.core.packet.dto.Identity;
import io.mosip.registration.processor.core.spi.packetmanager.PacketInfoManager;
import io.mosip.registration.processor.packet.storage.dto.ApplicantInfoDto;
import io.mosip.registration.processor.packet.storage.service.impl.PacketInfoManagerImpl;
import io.mosip.registration.processor.status.service.AdditionalInfoRequestService;
import io.mosip.registration.processor.status.service.impl.AdditionalInfoRequestServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransactionBeanConfig {

    @Bean
    public AdditionalInfoRequestService additionalInfoRequestService() {
        return new AdditionalInfoRequestServiceImpl();
    }

    @Bean
    public PacketInfoManager<Identity, ApplicantInfoDto> getPacketInfoManager() {
        return new PacketInfoManagerImpl();
    }

}
