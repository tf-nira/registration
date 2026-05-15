package io.mosip.registration.processor.notification.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.processor.core.code.RegistrationExceptionTypeCode;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.workflow.dto.WorkflowCompletedEventDTO;
import io.mosip.registration.processor.notification.constants.ResultCode;
import io.mosip.registration.processor.notification.service.NotificationService;
import io.mosip.registration.processor.notification.service.impl.NotificationServiceImpl;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.dao.RegistrationStatusDao;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.dto.TransactionDto;
import io.mosip.registration.processor.status.entity.BaseRegistrationPKEntity;
import io.mosip.registration.processor.status.entity.RegistrationStatusEntity;
import io.mosip.registration.processor.status.service.NotificationMessageService;
import io.mosip.registration.processor.status.service.RegistrationStatusService;
import io.mosip.registration.processor.status.service.TransactionService;

import javax.annotation.PostConstruct;

@Component
public class NotificationScheduler {
	
	private static Logger regProcLogger = RegProcessorLogger.getLogger(NotificationScheduler.class);
	private static final String USER = "MOSIP_SYSTEM";
	
	@Value("${mosip.notification.scheduler.fetchsize:5}")
	private Integer fetchSize;
	
	@Value("#{T(java.util.Arrays).asList('${mosip.notification.scheduler.failed.exclude-stage-names:PacketReceiverStage,ManualAdjudicationStage}')}")
	private List<String> notificationFailedExcludeStageNames;
	
	@Value("#{T(java.util.Arrays).asList('${mosip.notification.scheduler.rejected.exclude-stage-names:PacketReceiverStage,ManualAdjudicationStage}')}")
	private List<String> notificationRejectedExcludeStageNames;

	@Value("${mosip.notification.scheduler.threads.count:30}")
	private Integer numberOfThreads;
	
	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;
	
	@Autowired
	private NotificationMessageService notificationMessageService;
	
	@Autowired
	TransactionService<TransactionDto> transactionService;
	
	@Autowired
	private NotificationService notificationService;
	
	@Autowired
	private RegistrationStatusDao registrationStatusDao;

	private ExecutorService executorService;

	@PostConstruct
	public void init() {
		this.executorService = Executors.newFixedThreadPool(numberOfThreads);
	}

	@Scheduled(cron = "${mosip.notification.scheduler.cron.expression:0 0/3 * * * ?}")
	public void sendNotifications() {
		regProcLogger.info("Batch job for notifications started");
		List<String> statusCodes = new ArrayList<String>();
		statusCodes.add(RegistrationStatusCode.PROCESSED.toString());
		statusCodes.add(RegistrationStatusCode.FAILED.toString());
		statusCodes.add(RegistrationStatusCode.REJECTED.toString());
		List<InternalRegistrationStatusDto> packets = registrationStatusService.getUnNotifiedPackets(fetchSize, statusCodes);

		regProcLogger.info("Records picked for sending notifications: " + packets.size());

		AtomicInteger notificationsSent = new AtomicInteger(0);

		List<CompletableFuture<Void>> allBatches = packets.stream().map(packet -> CompletableFuture
						.runAsync(() -> sendIndividualNotification(packet, notificationsSent), executorService))
				.collect(Collectors.toList());

		CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(allBatches.toArray(new CompletableFuture[0]));
		allOfFuture.join();
		regProcLogger.info("Batch job completed, notifications sent: " + notificationsSent.get());
	}

	private void sendIndividualNotification(InternalRegistrationStatusDto packet, AtomicInteger notificationsSent) {
		try {
			WorkflowCompletedEventDTO workflowDto = new WorkflowCompletedEventDTO();
			boolean sendNotification = true;

			workflowDto.setInstanceId(packet.getRegistrationId());
			workflowDto.setResultCode(packet.getStatusCode());
			workflowDto.setWorkflowType(packet.getRegistrationType());

			if (ResultCode.FAILED.toString().equals(packet.getStatusCode())) {
				if (!notificationFailedExcludeStageNames.contains(packet.getRegistrationStageName())) {
					TransactionDto dto = transactionService.getTransactionByRegIdAndStatusComment(packet.getRegistrationId(), "Packet processing completed with action code : COMPLETE_AS_PROCESSED");

					if (dto != null) {
						workflowDto.setResultCode(ResultCode.PROCESSED.toString());
					} else {
						if (Objects.equals(packet.getRegistrationStageName(), "PacketValidatorStage")) {
							workflowDto.setErrorCode(RegistrationExceptionTypeCode.REG_PACKET_REJECTED.name());
						} else {
							workflowDto.setErrorCode(RegistrationExceptionTypeCode.PACKET_FAILED.name());
						}

						Map<String, String> notificationAttibutes = notificationMessageService.getNotificationDetails(packet.getRegistrationId());
						workflowDto.setNotificationAttributes(notificationAttibutes);
					}
				} else {
					sendNotification = false;
				}
			} else if(ResultCode.REJECTED.toString().equals(packet.getStatusCode())) {
				if (!notificationFailedExcludeStageNames.contains(packet.getRegistrationStageName())) {
					TransactionDto dto = transactionService.getTransactionByRegIdAndStatusComment(packet.getRegistrationId(), "Packet processing completed with action code : COMPLETE_AS_PROCESSED");

					if (dto != null) {
						workflowDto.setResultCode(ResultCode.PROCESSED.toString());
					} else {
						if (packet.getRegistrationStageName().contains(ProviderStageName.MVS.getValue())) {
							workflowDto.setErrorCode(RegistrationExceptionTypeCode.MVS_PACKET_REJECTED.name());
						} else if (packet.getRegistrationStageName().contains(ProviderStageName.MANUAL_ADJUDICATION.getValue())) {
							workflowDto.setErrorCode(RegistrationExceptionTypeCode.MA_PACKET_REJECTED.name());
						} else {
							workflowDto.setErrorCode(RegistrationExceptionTypeCode.PACKET_REJECTED.name());
						}

						Map<String, String> notificationAttibutes = notificationMessageService.getNotificationDetails(packet.getRegistrationId());
						workflowDto.setNotificationAttributes(notificationAttibutes);
					}
				} else {
					sendNotification = false;
				}
			}

			if (sendNotification) {
				regProcLogger.info("Sending notification for rid: " + packet.getRegistrationId());
				boolean isSuccess = notificationService.sendNotificationProcess(workflowDto);

				if (isSuccess) {
					notificationsSent.incrementAndGet();
					packet.setNotificationSent(true);
					packet.setUpdatedBy(USER);
//					packet.setUpdateDateTime(LocalDateTime.now(ZoneId.of("UTC")));
					RegistrationStatusEntity entity = convertDtoToEntity(packet);
					registrationStatusDao.save(entity);
				} else {
					regProcLogger.info("Failed to send notification: " + packet.getRegistrationId());
				}
			}
		} catch (Exception e) {
			regProcLogger.error("Failed to send notification: " + e.getMessage() , e);
		}
	}
	
	private RegistrationStatusEntity convertDtoToEntity(InternalRegistrationStatusDto dto) {
		BaseRegistrationPKEntity pk = new BaseRegistrationPKEntity();
		pk.setWorkflowInstanceId(dto.getWorkflowInstanceId());

		RegistrationStatusEntity registrationStatusEntity = new RegistrationStatusEntity();
		registrationStatusEntity.setId(pk);
		registrationStatusEntity.setRegId(dto.getRegistrationId());
		registrationStatusEntity.setRegistrationType(dto.getRegistrationType());
		registrationStatusEntity.setIteration(dto.getIteration());
		registrationStatusEntity.setReferenceRegistrationId(dto.getReferenceRegistrationId());
		registrationStatusEntity.setStatusCode(dto.getStatusCode());
		registrationStatusEntity.setLangCode(dto.getLangCode());
		registrationStatusEntity.setStatusComment(dto.getStatusComment());
		registrationStatusEntity.setLatestRegistrationTransactionId(dto.getLatestRegistrationTransactionId());
		registrationStatusEntity.setIsActive(dto.isActive());
		registrationStatusEntity.setCreatedBy(dto.getCreatedBy());
		if (dto.getCreateDateTime() == null) {
			registrationStatusEntity.setCreateDateTime(LocalDateTime.now(ZoneId.of("UTC")));
		} else {
			registrationStatusEntity.setCreateDateTime(dto.getCreateDateTime());
		}
		registrationStatusEntity.setUpdatedBy(dto.getUpdatedBy());
		registrationStatusEntity.setUpdateDateTime(dto.getUpdateDateTime());
		registrationStatusEntity.setIsDeleted(dto.isDeleted());

		if (registrationStatusEntity.isDeleted() != null && registrationStatusEntity.isDeleted()) {
			registrationStatusEntity.setDeletedDateTime(LocalDateTime.now(ZoneId.of("UTC")));
		} else {
			registrationStatusEntity.setDeletedDateTime(null);
		}

		registrationStatusEntity.setRetryCount(dto.getRetryCount());
		registrationStatusEntity.setApplicantType(dto.getApplicantType());
		registrationStatusEntity.setRegProcessRetryCount(dto.getReProcessRetryCount());
		registrationStatusEntity.setLatestTransactionStatusCode(dto.getLatestTransactionStatusCode());
		registrationStatusEntity.setLatestTransactionTypeCode(dto.getLatestTransactionTypeCode());
		registrationStatusEntity.setRegistrationStageName(dto.getRegistrationStageName());
		registrationStatusEntity.setLatestTransactionTimes(dto.getLatestTransactionTimes());
		registrationStatusEntity.setResumeTimeStamp(dto.getResumeTimeStamp());
		registrationStatusEntity.setDefaultResumeAction(dto.getDefaultResumeAction());
		registrationStatusEntity.setNeedsNotification(dto.getNeedsNotification());
		registrationStatusEntity.setNotificationSent(dto.getNotificationSent());
		return registrationStatusEntity;
	}
}
