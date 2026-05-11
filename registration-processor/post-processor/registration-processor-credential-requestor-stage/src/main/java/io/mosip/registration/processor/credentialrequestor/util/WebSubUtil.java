package io.mosip.registration.processor.credentialrequestor.util;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.websub.model.EventModel;
import io.mosip.kernel.core.websub.spi.PublisherClient;
import io.mosip.kernel.websub.api.exception.WebSubClientException;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;

@Component
public class WebSubUtil {
	@Autowired
	private PublisherClient<String, EventModel, HttpHeaders> pb; 

	/** The config server file storage URL. */
	@Value("${websub.publish.url}")
	private String partnerhuburl;

	private static Logger regProcLogger = RegProcessorLogger.getLogger(WebSubUtil.class);

	@Retryable(value = { WebSubClientException.class,
			IOException.class }, maxAttemptsExpression = "${mosip.credential.stage.retry.maxAttempts}", backoff = @Backoff(delayExpression = "${mosip.credential.stage.retry.maxDelay}"))
	public void publishSuccess(String topic, EventModel eventModel) {
		String requestId = eventModel.getEvent().getTransactionId();
        HttpHeaders httpHeaders = new HttpHeaders();
		pb.publishUpdate(topic, eventModel, MediaType.APPLICATION_JSON_UTF8_VALUE, httpHeaders, partnerhuburl);
		regProcLogger.info(requestId, "Publish the update successfully");
		
	}

}
