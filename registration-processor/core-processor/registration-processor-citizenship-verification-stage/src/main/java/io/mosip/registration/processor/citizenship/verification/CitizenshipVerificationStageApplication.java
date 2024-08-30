package io.mosip.registration.processor.citizenship.verification;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import io.mosip.registration.processor.citizenship.verification.stage.CitizenshipVerificationStage;

public class CitizenshipVerificationStageApplication {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.scan("io.mosip.registration.processor.core.config",
				"io.mosip.registration.processor.rest.client.config",
				"io.mosip.registration.processor.core.kernel.beans",
				"io.mosip.registration.processor.status.config",
				"io.mosip.registration.processor.packet.storage.config",
				"io.mosip.registration.processor.citizenship.verification");
		ctx.refresh();

		CitizenshipVerificationStage cvStage = ctx.getBean(CitizenshipVerificationStage.class);
		cvStage.deployVerticle();
		

	}

}
