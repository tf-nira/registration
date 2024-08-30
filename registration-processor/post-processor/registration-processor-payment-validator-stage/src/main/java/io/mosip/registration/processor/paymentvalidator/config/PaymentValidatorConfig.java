package io.mosip.registration.processor.paymentvalidator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.DefaultOAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.token.DefaultAccessTokenRequest;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;
import org.springframework.web.client.RestTemplate;

import io.mosip.registration.processor.paymentvalidator.util.CustomizedRestApiClient;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("deprecation")
@EnableOAuth2Client
@Configuration
public class PaymentValidatorConfig {
	
	
	@Value("${security.oauth2.client.access-token-uri}")
	private String tokenUrl;

	@Value("${security.oauth2.client.client-id}")
	private String clientId;

	@Value("${security.oauth2.client.client-secret}")
	private String clientSecret;
	
	@Value("${security.oauth2.client.grant-type}")
	private String gatewayGrantType;

	@Bean
	public OAuth2RestOperations oauthTemplate() {
		
		ClientCredentialsResourceDetails resource = new ClientCredentialsResourceDetails();
	    
	    resource.setAccessTokenUri(tokenUrl);
	    resource.setClientId(clientId);
	    resource.setClientSecret(clientSecret);
	    resource.setGrantType(gatewayGrantType);
		
		
		
	    return new OAuth2RestTemplate(resource, new DefaultOAuth2ClientContext(new DefaultAccessTokenRequest()));
	}

	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder.build();
	}
	
	
	@Bean
	public CustomizedRestApiClient getCustomizedRestApiClient() {
		return new CustomizedRestApiClient();
	}

}
