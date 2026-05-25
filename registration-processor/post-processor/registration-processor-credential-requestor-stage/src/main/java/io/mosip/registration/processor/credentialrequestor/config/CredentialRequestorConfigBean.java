package io.mosip.registration.processor.credentialrequestor.config;

import io.mosip.registration.processor.credentialrequestor.util.CredentialPartnerUtil;
import io.mosip.registration.processor.credentialrequestor.util.WebSubUtil;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CredentialRequestorConfigBean {

	@Bean
	CredentialPartnerUtil credentialPartnerUtil() {
		return new CredentialPartnerUtil();
	}
	
	@Bean
	WebSubUtil webSubUtil() {
		return new WebSubUtil();
	}

}