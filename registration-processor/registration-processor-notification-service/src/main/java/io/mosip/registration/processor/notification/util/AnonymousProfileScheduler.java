package io.mosip.registration.processor.notification.util;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
import io.mosip.registration.processor.status.dto.TransactionDto;
import io.mosip.registration.processor.status.entity.AnonymousProfileEntity;
import io.mosip.registration.processor.status.entity.AnonymousProfilePKEntity;
import io.mosip.registration.processor.status.service.AnonymousProfileService;
import io.mosip.registration.processor.status.service.TransactionService;
import io.mosip.registration.processor.status.utilities.RegistrationUtility;

/**
 * Builds anonymous profiles for processed packets and stores them in
 * {@code regprc.anonymous_profile}.
 * <p>
 * Each run drains the backlog chunk by chunk (up to
 * {@code mosip.anonymous.profile.scheduler.max.run.seconds}). One chunk is one
 * database transaction:
 * <ol>
 * <li>lock the next {@code fetchsize} pending packets with
 * {@code FOR UPDATE SKIP LOCKED}, so any number of pods can run the job
 * concurrently without picking the same packet,</li>
 * <li>build the profiles in parallel (packet reads happen here),</li>
 * <li>batch insert the profiles and flag the packets in the same
 * transaction, so a crash can neither lose nor duplicate a profile.</li>
 * </ol>
 * Packets whose profile could not be built stay unflagged and are retried by
 * the next run. Within a run the job pages past them with a keyset cursor, so
 * a packet that keeps failing cannot block the ones behind it.
 */
@Component
public class AnonymousProfileScheduler {

	private static Logger regProcLogger = RegProcessorLogger.getLogger(AnonymousProfileScheduler.class);
	private static final String USER = "MOSIP_SYSTEM";
	private static final String UPDATED_BY = "anonymous";

	/** Upper bound of ids per UPDATE ... IN (...) statement. */
	private static final int MAX_IDS_PER_UPDATE = 1000;

	/** A chunk that fails this many times in a row stops the run. */
	private static final int MAX_CONSECUTIVE_CHUNK_FAILURES = 3;

	private static final Timestamp CURSOR_START = Timestamp.valueOf("1970-01-01 00:00:00");

	/** Used when no from/to date is configured, so the range is effectively open. */
	private static final Timestamp CREATED_FROM_OPEN = Timestamp.valueOf("1970-01-01 00:00:00");
	private static final Timestamp CREATED_TO_OPEN = Timestamp.valueOf("9999-12-31 00:00:00");

	/** Number of packets locked and processed per transaction. */
	@Value("${mosip.anonymous.profile.scheduler.fetchsize:1000}")
	private Integer fetchSize;

	@Value("${mosip.anonymous.profile.scheduler.threads.count:30}")
	private Integer numberOfThreads;

	@Value("${mosip.anonymous.profile.bioInfo.required:true}")
	        private boolean anonymousProfileBioInfoRequired;

	/**
	 * A run stops starting new chunks after this many seconds. Keep it below the
	 * cron interval so runs do not queue up behind each other.
	 */
	@Value("${mosip.anonymous.profile.scheduler.max.run.seconds:150}")
	private Integer maxRunSeconds;

	/**
	 * Optional, format yyyy-MM-dd. Only packets created (registration.cr_dtimes) on
	 * or after this date are processed. Empty means no lower limit.
	 */
	@Value("${mosip.anonymous.profile.scheduler.from.date:}")
	private String fromDate;

	/**
	 * Optional, format yyyy-MM-dd. Only packets created (registration.cr_dtimes) on
	 * or before this date are processed; the whole day is included. Empty means no
	 * upper limit.
	 */
	@Value("${mosip.anonymous.profile.scheduler.to.date:}")
	private String toDate;

	/** Rows per JDBC batch when inserting profiles. */
	@Value("${mosip.anonymous.profile.scheduler.insert.batch.size:500}")
	private Integer insertBatchSize;

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

	@Autowired
	private PlatformTransactionManager transactionManager;

	/** Workers that read packets and build profiles. */
	private ExecutorService executorService;

	/**
	 * Runs the job off the shared Spring scheduler thread, so a long run cannot
	 * delay the other scheduled jobs of this service.
	 */
	private ExecutorService jobExecutor;

	private TransactionTemplate transactionTemplate;

	private final AtomicBoolean running = new AtomicBoolean(false);

	private final Map<Double, Map<String, String>> schemaFieldTypesCache = new ConcurrentHashMap<>();
	private final Map<Double, List<String>> defaultFieldsCache = new ConcurrentHashMap<>();
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
	private final Map<Double, SchemaMetadata> schemaMetadataCache = new ConcurrentHashMap<>();

	private final Map<Double, AtomicInteger> schemaUsageCounter = new ConcurrentHashMap<>();

	/** A pending packet, as read from the registration table. */
	private static class PacketRef {
		final String workflowInstanceId;
		final String registrationId;
		final String registrationType;
		final String statusCode;
		final String registrationStageName;

		PacketRef(Object[] row) {
			this.workflowInstanceId = asString(row[0]);
			this.registrationId = asString(row[1]);
			this.registrationType = asString(row[2]);
			this.statusCode = asString(row[3]);
			this.registrationStageName = asString(row[4]);
		}
	}

	/** Keyset position: the last packet claimed so far in the current run. */
	private static class Cursor {
		Timestamp updDtimes = CURSOR_START;
		String workflowInstanceId = "";
		/** Packets created in [createdFrom, createdToExclusive) are processed. */
		Timestamp createdFrom = CREATED_FROM_OPEN;
		Timestamp createdToExclusive = CREATED_TO_OPEN;
	}

	private static class ChunkResult {
		final int claimed;
		final int added;

		ChunkResult(int claimed, int added) {
			this.claimed = claimed;
			this.added = added;
		}
	}

   JSONObject regProcessorIdentityJson = null;
   String idSchemaVersionValue = null;

	@PostConstruct
    public void init() {
        this.executorService = Executors.newFixedThreadPool(numberOfThreads, namedDaemonThreads("anonymous-profile-worker"));
		this.jobExecutor = Executors.newSingleThreadExecutor(namedDaemonThreads("anonymous-profile-job"));
		this.transactionTemplate = new TransactionTemplate(transactionManager);
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

	@PreDestroy
	public void shutdown() {
		if (executorService != null) {
			executorService.shutdownNow();
		}
		if (jobExecutor != null) {
			jobExecutor.shutdownNow();
		}
	}

	@Scheduled(cron = "${mosip.anonymous.profile.scheduler.cron.expression:0 0/3 * * * ?}")
	public void addAnonymousprofile() {
		if (!running.compareAndSet(false, true)) {
			regProcLogger.info("Previous anonymous profile batch job is still running on this pod, skipping this trigger");
			return;
		}
		try {
			jobExecutor.execute(() -> {
				try {
					runJob();
				} catch (Throwable t) {
					regProcLogger.error("Batch job for anonymous profile failed: " + t.getMessage(), t);
				} finally {
					running.set(false);
				}
			});
		} catch (RuntimeException e) {
			running.set(false);
			throw e;
		}
	}

	void runJob() {
		regProcLogger.info("Batch job for anonymous profile started");
		schemaUsageCounter.clear();
		long startedAt = System.nanoTime();
		long deadline = startedAt + TimeUnit.SECONDS.toNanos(maxRunSeconds);
		Cursor cursor = new Cursor();
		Timestamp[] createdRange = resolveCreatedRange();
		if (createdRange == null) {
			// misconfigured dates: do not run unfiltered by accident
			return;
		}
		cursor.createdFrom = createdRange[0];
		cursor.createdToExclusive = createdRange[1];
		regProcLogger.info("Anonymous profile packets created from {} (inclusive) to {} (exclusive) are processed",
				cursor.createdFrom, cursor.createdToExclusive);
		long claimed = 0;
		long added = 0;
		int consecutiveFailures = 0;

		while (System.nanoTime() < deadline) {
			long chunkStartedAt = System.nanoTime();
			ChunkResult result;
			try {
				result = transactionTemplate.execute(status -> processChunk(cursor));
			} catch (RuntimeException e) {
				// the chunk was rolled back; the cursor has already moved past it, so the
				// next chunk is not blocked by it. It is retried by the next run.
				consecutiveFailures++;
				regProcLogger.error("Anonymous profile chunk failed and was rolled back ("
						+ consecutiveFailures + " in a row): " + e.getMessage(), e);
				if (consecutiveFailures >= MAX_CONSECUTIVE_CHUNK_FAILURES) {
					break;
				}
				continue;
			}
			consecutiveFailures = 0;
			if (result == null || result.claimed == 0) {
				break;
			}
			claimed += result.claimed;
			added += result.added;
			regProcLogger.info("Anonymous profile chunk done: claimed {}, added {}, took {} ms", result.claimed,
					result.added, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - chunkStartedAt));
		}

		if (!schemaUsageCounter.isEmpty()) {
			regProcLogger.info("Schema version usage in batch: {}", schemaUsageCounter);
		}
		regProcLogger.info("Batch job for anonymous profile completed. Claimed: {}, added: {}, not added: {}, took {} ms",
				claimed, added, claimed - added, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
	}

	/**
	 * Processes one chunk. Must run inside the transaction opened by
	 * {@link #runJob()}, which owns the row locks taken here.
	 */
	private ChunkResult processChunk(Cursor cursor) {
		List<Object[]> rows = registrationStatusDao.getAnonymousNotAddedPacketsForUpdate(cursor.updDtimes,
				cursor.workflowInstanceId, cursor.createdFrom, cursor.createdToExclusive, fetchSize);
		if (rows == null || rows.isEmpty()) {
			return new ChunkResult(0, 0);
		}

		List<PacketRef> packets = new ArrayList<>(rows.size());
		for (Object[] row : rows) {
			packets.add(new PacketRef(row));
		}
		// move the cursor before doing any work, so a failing chunk is skipped by the run
		Object[] lastRow = rows.get(rows.size() - 1);
		cursor.updDtimes = toTimestamp(lastRow[5]);
		cursor.workflowInstanceId = asString(lastRow[0]);

		List<CompletableFuture<AnonymousProfileEntity>> futures = new ArrayList<>(packets.size());
		for (PacketRef packet : packets) {
			futures.add(CompletableFuture.supplyAsync(() -> buildProfile(packet), executorService));
		}

		List<AnonymousProfileEntity> profiles = new ArrayList<>(packets.size());
		List<String> processedWorkflowInstanceIds = new ArrayList<>(packets.size());
		for (int i = 0; i < packets.size(); i++) {
			AnonymousProfileEntity profile = futures.get(i).join();
			if (profile != null) {
				profiles.add(profile);
				processedWorkflowInstanceIds.add(packets.get(i).workflowInstanceId);
			}
		}

		if (!profiles.isEmpty()) {
			anonymousProfileService.saveAnonymousProfilesBatch(profiles, insertBatchSize);
			markAsAdded(processedWorkflowInstanceIds);
		}
		return new ChunkResult(packets.size(), profiles.size());
	}

	/**
	 * Turns the configured from/to dates into [from, to + 1 day), so the whole end
	 * day is included. Returns null, after logging the reason, if a date cannot be
	 * parsed or from is after to.
	 */
	private Timestamp[] resolveCreatedRange() {
		try {
			LocalDate from = parseDate(fromDate);
			LocalDate to = parseDate(toDate);
			if (from != null && to != null && from.isAfter(to)) {
				regProcLogger.error("Anonymous profile job not run: from.date {} is after to.date {}", from, to);
				return null;
			}
			Timestamp createdFrom = from == null ? CREATED_FROM_OPEN : Timestamp.valueOf(from.atStartOfDay());
			Timestamp createdToExclusive = to == null ? CREATED_TO_OPEN
					: Timestamp.valueOf(to.plusDays(1).atStartOfDay());
			return new Timestamp[] { createdFrom, createdToExclusive };
		} catch (DateTimeParseException e) {
			regProcLogger.error("Anonymous profile job not run: from.date/to.date must be yyyy-MM-dd but were '"
					+ fromDate + "' / '" + toDate + "'", e);
			return null;
		}
	}

	private static LocalDate parseDate(String value) {
		return value == null || value.trim().isEmpty() ? null : LocalDate.parse(value.trim());
	}

	private void markAsAdded(List<String> workflowInstanceIds) {
		for (int from = 0; from < workflowInstanceIds.size(); from += MAX_IDS_PER_UPDATE) {
			List<String> part = workflowInstanceIds.subList(from,
					Math.min(from + MAX_IDS_PER_UPDATE, workflowInstanceIds.size()));
			int updated = registrationStatusDao.markAnonymousProfileAdded(part, UPDATED_BY);
			if (updated != part.size()) {
				regProcLogger.warn("Expected to flag {} packets as anonymous profile added but {} rows were updated",
						part.size(), updated);
			}
		}
	}

	private AnonymousProfileEntity newAnonymousProfileEntity(String processStage, String profileJson) {
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
		return anonymousProfileEntity;
	}

	/**
	 * Builds the profile of one packet. Returns null if it could not be built, in
	 * which case the packet is left unflagged and retried by a later run.
	 */
	private AnonymousProfileEntity buildProfile(PacketRef packet) {
		try {
			String registrationId = packet.registrationId;
			String registrationType = packet.registrationType;

			regProcLogger.debug("Adding anonymous profile for registration id {}", registrationId);

			if (packet.registrationStageName == null) {
				// process_stage is NOT NULL; fail this packet instead of the whole chunk insert
				throw new IllegalStateException("registration stage name is missing");
			}

			String schemaVersion = packetManagerService.getFieldByMappingJsonKey(registrationId, idSchemaVersionValue,
					registrationType, ProviderStageName.WORKFLOW_MANAGER);
			Double schemaVersionDouble = Double.parseDouble(schemaVersion);

			SchemaMetadata metadata = getOrLoadSchemaMetadata(schemaVersionDouble);
			Map<String, String> fieldTypeMap = metadata.fieldTypes;
			List<String> defaultFields = metadata.defaultFields;

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
			String json = anonymousProfileService.buildJsonStringFromPacketInfo(biometricRecord, fieldMap, fieldTypeMap,
					metaInfoMap, packet.statusCode, packet.registrationStageName);
			return newAnonymousProfileEntity(packet.registrationStageName, json);
		} catch (Exception e) {
			regProcLogger.error("Failed to add anonymous profile: " + e.getMessage(), e);
			return null;
		}
	}

	private SchemaMetadata getOrLoadSchemaMetadata(Double schemaVersion) {
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

			// Create and cache unified metadata
			SchemaMetadata metadata = new SchemaMetadata(schemaVersion, fieldTypes, defaultFields);

			SchemaMetadata existing = schemaMetadataCache.putIfAbsent(schemaVersion, metadata);
			SchemaMetadata result = (existing != null) ? existing : metadata;

			// Also populate individual caches for compatibility
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

	private static String asString(Object value) {
		return value == null ? null : value.toString();
	}

	private static Timestamp toTimestamp(Object value) {
		if (value instanceof Timestamp) {
			return (Timestamp) value;
		}
		if (value instanceof LocalDateTime) {
			return Timestamp.valueOf((LocalDateTime) value);
		}
		if (value instanceof java.util.Date) {
			return new Timestamp(((java.util.Date) value).getTime());
		}
		throw new IllegalStateException("Unexpected upd_dtimes type: " + (value == null ? null : value.getClass()));
	}

	private static java.util.concurrent.ThreadFactory namedDaemonThreads(String prefix) {
		AtomicInteger counter = new AtomicInteger(1);
		return runnable -> {
			Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
			thread.setDaemon(true);
			return thread;
		};
	}

}
