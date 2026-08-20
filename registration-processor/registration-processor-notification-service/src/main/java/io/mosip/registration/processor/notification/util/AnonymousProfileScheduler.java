package io.mosip.registration.processor.notification.util;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.packet.storage.utils.IdSchemaUtil;
import io.mosip.registration.processor.packet.storage.utils.PacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.status.dao.RegistrationStatusDao;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.dto.TransactionDto;
import io.mosip.registration.processor.status.entity.AnonymousProfileEntity;
import io.mosip.registration.processor.status.entity.AnonymousProfilePKEntity;
import io.mosip.registration.processor.status.entity.BaseRegistrationPKEntity;
import io.mosip.registration.processor.status.entity.RegistrationStatusEntity;
import io.mosip.registration.processor.status.service.AnonymousProfileService;
import io.mosip.registration.processor.status.service.RegistrationStatusService;
import io.mosip.registration.processor.status.service.TransactionService;
import io.mosip.registration.processor.status.utilities.RegistrationUtility;

@Component
public class AnonymousProfileScheduler {
	
	private static Logger regProcLogger = RegProcessorLogger.getLogger(AnonymousProfileScheduler.class);
	private static final String USER = "MOSIP_SYSTEM";
	
	@Value("${mosip.anonymous.profile.scheduler.fetchsize:1000}")
	private Integer fetchSize;
	
	@Value("${mosip.anonymous.profile.scheduler.threads.count:30}")
	private Integer numberOfThreads;
	
	@Value("${mosip.anonymous.profile.bioInfo.required:true}")
        private boolean anonymousProfileBioInfoRequired;
	
	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;
	
	@Autowired
	TransactionService<TransactionDto> transactionService;
	
	@Autowired
	private RegistrationStatusDao registrationStatusDao;
	
	@Autowired
	private Utilities utility;
	
	@Autowired
	private IdSchemaUtil idSchemaUtil;

	@Autowired
	private PacketManagerService packetManagerService;
	
	@Autowired
	private AnonymousProfileService anonymousProfileService;
	
	private ExecutorService executorService;

	// Optimization 1: Thread-safe cache for schema field types to avoid redundant lookups
	private final Map<Double, Map<String, String>> schemaFieldTypesCache = new ConcurrentHashMap<>();
	
	// Optimization 2: Thread-safe cache for default fields to avoid redundant lookups
	private final Map<Double, List<String>> defaultFieldsCache = new ConcurrentHashMap<>();
	
	// Optimization 3: Unified schema metadata for batch operations
	private static class SchemaMetadata {
		final Double schemaVersion;
		final Map<String, String> fieldTypes;
		final List<String> defaultFields;
		
		SchemaMetadata(Double version, Map<String, String> types, List<String> defaults) {
			this.schemaVersion = version;
			this.fieldTypes = types;
			this.defaultFields = defaults;
		}
	}
	// Thread-safe cache for unified schema metadata
	private final Map<Double, SchemaMetadata> schemaMetadataCache = new ConcurrentHashMap<>();
	
   JSONObject regProcessorIdentityJson = null;
   String idSchemaVersionValue = null;
   List<RegistrationStatusEntity> toBeUpdatedRegStatusRecords = new ArrayList<>();
   List<AnonymousProfileEntity> toBeUpdatedAnonymousProfiles = new ArrayList<>();
   
   // Optimization 4: Thread-safe counter for schema usage tracking
   private final Map<Double, AtomicInteger> schemaUsageCounter = new ConcurrentHashMap<>();
   
	@PostConstruct
    public void init() {
        this.executorService = Executors.newFixedThreadPool(numberOfThreads);		
		try {
			regProcessorIdentityJson = utility
					.getRegistrationProcessorMappingJson(MappingJsonConstants.IDENTITY);
			idSchemaVersionValue = JsonUtil.getJSONValue(
					JsonUtil.getJSONObject(regProcessorIdentityJson, MappingJsonConstants.IDSCHEMA_VERSION),
					MappingJsonConstants.VALUE);

		} catch (IOException e) {
			regProcLogger.error("Failed to initialize AnonymousProfileScheduler: " + e.getMessage(), e);
		}
    }

	@Scheduled(cron = "${mosip.anonymous.profile.scheduler.cron.expression:0 0/3 * * * ?}")
	public void addAnonymousprofile() {
		regProcLogger.info("Batch job for anonymous profile started");
		toBeUpdatedRegStatusRecords.clear();
		toBeUpdatedAnonymousProfiles.clear();

		List<InternalRegistrationStatusDto> packets = getAnonymousNotAddedPackets();
		regProcLogger.info("Records picked for adding anonymous profile: " + packets.size());

		List<CompletableFuture<Void>> allBatches = packets.stream().map(packet -> CompletableFuture
				.runAsync(() -> insertAnonymousProfile(packet), executorService).exceptionally(ex -> {
					regProcLogger.error("Error processing packet: " + ex.getMessage(), ex);
					return null;
				})).collect(Collectors.toList());

		CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(allBatches.toArray(new CompletableFuture[0]));
		allOfFuture.join();
		
		if (toBeUpdatedRegStatusRecords.size() > 0) {
			updateRegistartionRecords(toBeUpdatedRegStatusRecords);
		}
		if(toBeUpdatedAnonymousProfiles.size() > 0) {
			insertAnonymousProfiles(toBeUpdatedAnonymousProfiles);
		}

		// Optimization 4: Log schema usage statistics for monitoring
		if (!schemaUsageCounter.isEmpty()) {
			regProcLogger.info("Schema version usage in batch: {}", schemaUsageCounter);
		}
		regProcLogger.info("Batch job for anonymous profile completed. Processed: {} records", packets.size());
	}

	@Transactional(readOnly = true)
	public List<InternalRegistrationStatusDto> getAnonymousNotAddedPackets(){
		return registrationStatusService.getAnonymousNotAddedPackets(fetchSize);
	}
	
	@Transactional
	public void updateRegistartionRecords(List<RegistrationStatusEntity> toBeUpdatedList) {
		registrationStatusDao.saveAll(toBeUpdatedList);
	}
	
	public void insertAnonymousProfiles(List<AnonymousProfileEntity> toBeUpdatedAnonymousProfiles) {
		anonymousProfileService.saveAnonymousProfiles(toBeUpdatedAnonymousProfiles);
	}	
	
	private void addToBeUpdatedAnonymousProfileList(String regId, String processStage, String profileJson) {
		AnonymousProfileEntity anonymousProfileEntity=new AnonymousProfileEntity();
		AnonymousProfilePKEntity anonymousProfilePKEntity=new AnonymousProfilePKEntity();
		anonymousProfilePKEntity.setId(RegistrationUtility.generateId());
		anonymousProfileEntity.setId(anonymousProfilePKEntity);
		anonymousProfileEntity.setProfile(profileJson);
		anonymousProfileEntity.setProcessStage(processStage);
		anonymousProfileEntity.setCreatedBy("SYSTEM");
		anonymousProfileEntity.setCreateDateTime(LocalDateTime.now(ZoneId.of("UTC")));
		anonymousProfileEntity.setUpdateDateTime(LocalDateTime.now(ZoneId.of("UTC")));
		anonymousProfileEntity.setIsDeleted(false);
		toBeUpdatedAnonymousProfiles.add(anonymousProfileEntity);
	}
	private void insertAnonymousProfile(InternalRegistrationStatusDto packet) {
		try {
			String json = null;
			String registrationId = packet.getRegistrationId();
			String registrationType = packet.getRegistrationType();

			regProcLogger.info("Adding anonymous profile for registration id {}", registrationId);
			
			String schemaVersion = packetManagerService.getFieldByMappingJsonKey(registrationId, idSchemaVersionValue,
					registrationType, ProviderStageName.WORKFLOW_MANAGER);
			Double schemaVersionDouble = Double.parseDouble(schemaVersion);

			// Optimization 1+2+3: Use unified schema metadata cache
			SchemaMetadata metadata = getOrLoadSchemaMetadata(schemaVersionDouble);
			Map<String, String> fieldTypeMap = metadata.fieldTypes;
			List<String> defaultFields = metadata.defaultFields;
			
			// Optimization 4: Thread-safe schema usage tracking using AtomicInteger
			schemaUsageCounter.computeIfAbsent(schemaVersionDouble, k -> new AtomicInteger(0)).incrementAndGet();
			
			regProcLogger.debug("Using cached schema metadata for version: {}. Cache size: {}", 
				schemaVersion, schemaMetadataCache.size());
			
			// Optimization 2: Get fields and metadata in batch
			Map<String, String> fieldMap = packetManagerService.getFields(registrationId,
					defaultFields, registrationType,
					ProviderStageName.WORKFLOW_MANAGER);
			Map<String, String> metaInfoMap = packetManagerService.getMetaInfo(registrationId, registrationType,
					ProviderStageName.WORKFLOW_MANAGER);
			BiometricRecord biometricRecord = null;
			if(anonymousProfileBioInfoRequired) {
				biometricRecord = packetManagerService.getBiometrics(registrationId,
						MappingJsonConstants.INDIVIDUAL_BIOMETRICS, registrationType, ProviderStageName.WORKFLOW_MANAGER);				
			}
			json = anonymousProfileService.buildJsonStringFromPacketInfo(biometricRecord, fieldMap, fieldTypeMap,
					metaInfoMap, packet.getStatusCode(), packet.getRegistrationStageName());
			addToBeUpdatedAnonymousProfileList(registrationId, packet.getRegistrationStageName(), json);
			convertAndAddToBeUpdatedRegStatusRecords(packet);
		} catch (Exception e) {
			regProcLogger.error("Failed to add anonymous profile for registration: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Get or load schema metadata from cache. If not cached, fetch and cache it.
	 * This method is thread-safe and optimizes schema lookups by keeping both
	 * field types and default fields in a single cached object.
	 * Uses putIfAbsent to ensure only one thread loads the metadata even in concurrent scenarios.
	 */
	private SchemaMetadata getOrLoadSchemaMetadata(Double schemaVersion) {
		// Optimization 3: Check unified metadata cache first (thread-safe get)
		SchemaMetadata cached = schemaMetadataCache.get(schemaVersion);
		if (cached != null) {
			regProcLogger.debug("Cache HIT for schema metadata: version={}", schemaVersion);
			return cached;
		}
		
		regProcLogger.debug("Cache MISS for schema metadata: version={}. Loading...", schemaVersion);
		
		try {
			// Fetch both field types and default fields
			Map<String, String> fieldTypes = idSchemaUtil.getIdSchemaFieldTypes(schemaVersion);
			List<String> defaultFields = idSchemaUtil.getDefaultFields(schemaVersion);
			
			// Create unified metadata
			SchemaMetadata metadata = new SchemaMetadata(schemaVersion, fieldTypes, defaultFields);
			
			// Thread-safe put: putIfAbsent only puts if key doesn't exist
			// If another thread already cached it, use theirs instead
			SchemaMetadata existing = schemaMetadataCache.putIfAbsent(schemaVersion, metadata);
			SchemaMetadata result = (existing != null) ? existing : metadata;
			
			// Also populate individual caches for compatibility (thread-safe)
			schemaFieldTypesCache.putIfAbsent(schemaVersion, fieldTypes);
			defaultFieldsCache.putIfAbsent(schemaVersion, defaultFields);
			
			if (existing == null) {
				// Only log if we actually cached it (not if another thread beat us to it)
				regProcLogger.info("Loaded and cached schema metadata: version={}, fieldTypes={}, defaultFields={}",
					schemaVersion, fieldTypes.size(), defaultFields.size());
			} else {
				regProcLogger.debug("Another thread already cached schema metadata for version: {}", schemaVersion);
			}
			
			return result;
		} catch (Exception e) {
			regProcLogger.error("Failed to load schema metadata for version {}: {}", schemaVersion, e.getMessage());
			throw new RuntimeException("Failed to load schema metadata", e);
		}
	}

	private void convertAndAddToBeUpdatedRegStatusRecords(InternalRegistrationStatusDto dto) {
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
		registrationStatusEntity.setUpdatedBy("anonymous");
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
		registrationStatusEntity.setIsAnonymousProfileAdded(true);
		toBeUpdatedRegStatusRecords.add(registrationStatusEntity);
	}
	
}
