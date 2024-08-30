package io.mosip.registration.processor.paymentvalidator;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import io.mosip.registration.processor.paymentvalidator.stage.PaymentValidatorStage;

public class PaymentValidatorStageApplication {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.scan("io.mosip.registration.processor.core.config", "io.mosip.registration.processor.print.config",
				"io.mosip.registration.processor.rest.client.config",
				"io.mosip.registration.processor.core.kernel.beans",
				"io.mosip.registration.processor.status.config",
				"io.mosip.registration.processor.packet.storage.config",
				"io.mosip.registration.processor.paymentvalidator.util",
				"io.mosip.registration.processor.paymentvalidator.config");
		ctx.refresh();

		PaymentValidatorStage paymentStage = ctx.getBean(PaymentValidatorStage.class);
		paymentStage.deployVerticle();

	}
}
