package io.mosip.registration.processor.stages.legacy.data.val.stage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.processor.core.abstractverticle.MessageBusAddress;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.abstractverticle.MosipEventBus;
import io.mosip.registration.processor.core.abstractverticle.MosipRouter;
import io.mosip.registration.processor.core.abstractverticle.MosipVerticleAPIManager;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;



@Component
@Configuration
@ComponentScan(basePackages = { "${mosip.auth.adapter.impl.basepackage}",
		"io.mosip.registration.processor.core.config",
		"io.mosip.registration.processor.stages.legacy.data",
		"io.mosip.registration.processor.stages.config", 
		"io.mosip.registrationprocessor.stages.config", 
		"io.mosip.registration.processor.status.config",
		"io.mosip.registration.processor.rest.client.config", 
		"io.mosip.registration.processor.packet.storage.config",
		"io.mosip.registration.processor.packet.manager.config", 
		"io.mosip.registration.processor.core.kernel.beans" })
public class LegacyDataStage extends MosipVerticleAPIManager {
	
	private static final String STAGE_PROPERTY_PREFIX = "mosip.regproc.legacy.data.";

	/** The reg proc logger. */
	private static Logger regProcLogger = RegProcessorLogger.getLogger(LegacyDataStage.class);

	/** The cluster url. */
	@Value("${vertx.cluster.configuration}")
	private String clusterManagerUrl;

	/** worker pool size. */
	@Value("${worker.pool.size}")
	private Integer workerPoolSize;

	/** After this time intervel, message should be considered as expired (In seconds). */
	@Value("${mosip.regproc.legacy.data.message.expiry-time-limit}")
	private Long messageExpiryTimeLimit;

	/**
	 * The mosip event bus.
	 */
	private MosipEventBus mosipEventBus;
	
	@Autowired
	LegacyDataProcessor legacyDataProcessor;


	/** Mosip router for APIs */
	@Autowired
	MosipRouter router;
	
	@Override
	protected String getPropertyPrefix() {
		return STAGE_PROPERTY_PREFIX;
	}

	/**
	 * Deploy verticle.
	 */
	public void deployVerticle() {
		this.mosipEventBus = this.getEventBus(this, clusterManagerUrl, workerPoolSize);
		this.consume(mosipEventBus, MessageBusAddress.LEGACY_DATA_IN, messageExpiryTimeLimit);
		try {
			legacyDataProcessor.connectAndSubscribe();
		} catch (Exception e) {
			regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(),"Unknown Exception while connecting and subscribing to graphQL : {}",
					e.getMessage());
		}
	}

	@Override
	public void start(){
		router.setRoute(this.postUrl(getVertx(), MessageBusAddress.LEGACY_DATA_IN, MessageBusAddress.LEGACY_DATA_OUT));
		this.createServer(router.getRouter(), getPort());
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * io.mosip.registration.processor.core.spi.eventbus.EventBusManager#process(
	 * java.lang.Object)
	 */
	@Override
	public MessageDTO process(MessageDTO messageDTO) {
		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
				"LegacyDataStage::process()::entry");

		messageDTO.setMessageBusAddress(MessageBusAddress.LEGACY_DATA_IN);
		messageDTO.setInternalError(Boolean.FALSE);
		messageDTO.setIsValid(Boolean.FALSE);
		messageDTO = legacyDataProcessor.process(messageDTO, getStageName());

		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
				"LegacyDataStage::process()::exit", messageDTO.toString());

		return messageDTO;
	}
	/**
	 * Gets the stage name.
	 *
	 * @return the stage name
	 */
	protected String getStageName() {
		return ClassUtils.getUserClass(this.getClass()).getSimpleName();
	}
	
	public void sendMessage(MessageDTO messageDTO) {
		this.send(this.mosipEventBus, MessageBusAddress.LEGACY_DATA_OUT, messageDTO);
	}
}
