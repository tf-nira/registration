package io.mosip.registration.processor.citizenship.verification.stage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.processor.core.abstractverticle.MessageBusAddress;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.abstractverticle.MosipEventBus;
import io.mosip.registration.processor.core.abstractverticle.MosipRouter;
import io.mosip.registration.processor.core.abstractverticle.MosipVerticleAPIManager;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;

@ComponentScan(basePackages = { "${mosip.auth.adapter.impl.basepackage}","io.mosip.registration.processor.stages.config",
		"io.mosip.registration.processor.rest.client.config", "io.mosip.registration.processor.status.config", "io.mosip.registration.processor.core.kernel.beans",
		"io.mosip.registration.processor.core.config", "io.mosip.registration.processor.packet.storage.config","io.mosip.registration.processor.packet.manager.config",
		"io.mosip.registration.processor.citizenship.verification.stage", "io.mosip.registration.processor.citizenship.verification.service","io.mosip.registration.processor.stages.config",
		"io.mosip.registration.processor.citizenship.verification.util", "io.mosip.registration.processor.message.sender.template", "io.mosip.registration.processor.core.util" })
public class CitizenshipVerificationStage extends MosipVerticleAPIManager{

	private static final String STAGE_PROPERTY_PREFIX = "mosip.regproc.citizenshipverification.";
	private static Logger regProcLogger = RegProcessorLogger.getLogger(CitizenshipVerificationStage.class);
	
	/** The mosip event bus. */
	private MosipEventBus mosipEventBus;

	/**
	 * After this time intervel, message should be considered as expired (In
	 * seconds).
	 */
	@Value("${mosip.regproc.citizenshipverification.message.expiry-time-limit}")
	private Long messageExpiryTimeLimit;
	
	private static final String DATETIME_PATTERN = "mosip.registration.processor.datetime.pattern";

	/** The cluster manager url. */
	@Value("${vertx.cluster.configuration}")
	private String clusterManagerUrl;

	/** worker pool size. */
	@Value("${worker.pool.size}")
	private Integer workerPoolSize;

	/** Mosip router for APIs */
	@Autowired
	MosipRouter router;
	
	@Autowired
	CitizenshipVerificationProcessor citizenshipVerificationProcessor;
	
	@Override
	public MessageDTO process(MessageDTO object) {
		return citizenshipVerificationProcessor.process(object);
	}
	
	@Override
	public void deployVerticle() {
		mosipEventBus = this.getEventBus(this, clusterManagerUrl, workerPoolSize);
		this.consumeAndSend(mosipEventBus, MessageBusAddress.CITIZENSHIP_VERIFICATION_BUS_IN,
				MessageBusAddress.CITIZENSHIP_VERIFICATION_BUS_OUT, messageExpiryTimeLimit);
	}



	@Override
	public void start() {
		router.setRoute(this.postUrl(getVertx(), MessageBusAddress.CITIZENSHIP_VERIFICATION_BUS_IN,
				MessageBusAddress.CITIZENSHIP_VERIFICATION_BUS_OUT));
		this.createServer(router.getRouter(), getPort());
	}
	
	@Override
	protected String getPropertyPrefix() {
		return STAGE_PROPERTY_PREFIX;
	}
}
