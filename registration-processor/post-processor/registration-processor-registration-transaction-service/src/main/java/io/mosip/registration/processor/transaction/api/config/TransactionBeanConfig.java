package io.mosip.registration.processor.transaction.api.config;

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

}