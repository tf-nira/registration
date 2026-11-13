/*
 * 
 */
package io.mosip.registration.processor.status.repositary;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.mosip.kernel.core.dataaccess.spi.repository.BaseRepository;
import io.mosip.registration.processor.status.entity.BaseRegistrationEntity;
import io.mosip.registration.processor.status.entity.RegistrationStatusEntity;

/**
 * The Interface RegistrationRepositary.
 *
 * @param <T>
 *            the generic type
 * @param <E>
 *            the element type
 */
@Repository
public interface RegistrationRepositary<T extends BaseRegistrationEntity, E> extends BaseRepository<T, E> {
	

	@Query("SELECT registration.regId FROM RegistrationStatusEntity registration WHERE registration.regId in :regIds AND registration.statusCode in :statusCode")
	public List<String> getProcessedOrProcessingRegIds(@Param("regIds") List<String> regIds,
			@Param("statusCode") List<String> statusCode);

	@Query("SELECT registration FROM RegistrationStatusEntity registration WHERE registration.regId in :regIds and registration.statusCode !=:statusCode")
	public List<RegistrationStatusEntity> getWithoutStatusCode(@Param("regIds") List<String> regIds,
			@Param("statusCode") String statusCode);
	@Query("SELECT registration FROM RegistrationStatusEntity registration WHERE registration.regId = :regId AND registration.isDeleted =false AND registration.isActive=true")
	public List<RegistrationStatusEntity> findByRegId(@Param("regId") String regId);

	@Query("SELECT registration FROM RegistrationStatusEntity registration WHERE registration.regId = :regId AND registration.registrationType = :registrationType AND registration.isDeleted =false AND registration.isActive=true")
	public List<RegistrationStatusEntity> findByProcessANDRegId(@Param("regId") String regId, @Param("registrationType") String registrationType);
	
	@Query("SELECT registration FROM RegistrationStatusEntity registration WHERE registration.regId IN :regIds AND registration.isDeleted =false AND registration.isActive=true")
	public List<RegistrationStatusEntity> findByRegIds(@Param("regIds") List<String> regIds);
	
	@Query("SELECT registration FROM RegistrationStatusEntity registration WHERE registration.regId IN :regIds AND registration.isDeleted =false AND registration.isActive=true order by registration.createDateTime")
	public List<RegistrationStatusEntity> findByRegIdsOrderbyCreatedDateTime(@Param("regIds") List<String> regIds);
	
	@Query("SELECT registration FROM RegistrationStatusEntity registration WHERE registration.statusCode = :statusCode AND registration.isDeleted =false AND registration.isActive=true")
	public List<RegistrationStatusEntity> findByStatusCode(@Param("statusCode") String statusCode);
	
	@Query("SELECT registration FROM RegistrationStatusEntity registration WHERE registration.regId = :regId AND registration.statusCode = :statusCode ")
	public List<RegistrationStatusEntity> findByRegIdANDByStatusCode(@Param("regId") String regId,@Param("statusCode") String statusCode);
	
	@Query("SELECT registration FROM RegistrationStatusEntity registration WHERE registration.id.workflowInstanceId = :workflowInstanceId AND registration.isDeleted =false AND registration.isActive=true")
	public List<RegistrationStatusEntity> findByWorkflowInstanceId(@Param("workflowInstanceId") String workflowInstanceId);

	@Query(value = "SELECT * FROM registration r WHERE r.latest_trn_status_code IN :status AND r.reg_process_retry_count<=:reprocessCount AND r.latest_trn_dtimes <:timeDifference AND r.status_code NOT IN :statusCodes AND r.reg_stage_name NOT IN :excludeStageNames  order by r.latest_trn_dtimes LIMIT :fetchSize ", nativeQuery = true)
	public List<RegistrationStatusEntity> getUnProcessedPackets(@Param("status") List<String> status,@Param("reprocessCount") Integer reprocessCount,@Param("timeDifference") LocalDateTime timeDifference,@Param("statusCodes") List<String> statusCodes,@Param("fetchSize") Integer fetchSize,@Param("excludeStageNames") List<String> excludeStageNames);

	@Query("SELECT COUNT(*) FROM RegistrationStatusEntity registration WHERE registration.latestTransactionStatusCode IN :status AND registration.regProcessRetryCount<=:reprocessCount AND registration.latestTransactionTimes<:timeDifference AND registration.statusCode  NOT IN :statusCodes AND registration.registrationStageName NOT IN :excludeStageNames")
	public int getUnProcessedPacketsCount(@Param("status") List<String> status,@Param("reprocessCount") Integer reprocessCount,@Param("timeDifference") LocalDateTime timeDifference,@Param("statusCodes") List<String> statusCodes,@Param("excludeStageNames") List<String> excludeStageNames);
	
	@Query(value ="SELECT * FROM registration r WHERE r.status_code IN :statusCodes AND r.resume_timestamp < now() AND r.default_resume_action is NOT NULL order by r.upd_dtimes LIMIT :fetchSize ", nativeQuery = true)
	public List<RegistrationStatusEntity> getActionablePausedPackets(@Param("statusCodes") List<String> statusCodes,@Param("fetchSize") Integer fetchSize);

	@Query(value = "SELECT * FROM registration r WHERE r.status_code =:statusCode AND r.reg_stage_name NOT IN :excludeStageNames order by r.upd_dtimes LIMIT :fetchSize ", nativeQuery = true)
	public List<RegistrationStatusEntity> getResumablePackets(@Param("statusCode") String statusCode,
															  @Param("fetchSize") Integer fetchSize, @Param("excludeStageNames") List<String> excludeStageNames);

	@Query(value ="SELECT * FROM registration r WHERE r.status_code IN :statusCodes and r.needs_notification=true and r.notification_sent IS NOT TRUE order by r.upd_dtimes LIMIT :fetchSize ", nativeQuery = true)
	public List<RegistrationStatusEntity> getUnNotifiedPackets(@Param("statusCodes") List<String> statusCodes,@Param("fetchSize") Integer fetchSize);
	
	@Query(value ="SELECT * FROM registration r WHERE r.upd_dtimes < CURRENT_DATE and r.is_anonymous_profile_added IS NOT TRUE order by r.upd_dtimes LIMIT :fetchSize ", nativeQuery = true)
	public List<RegistrationStatusEntity> getAnonymousNotAddedPackets(@Param("fetchSize") Integer fetchSize);

	/**
	 * Claims the next chunk of packets that still need an anonymous profile.
	 * <p>
	 * Must be called inside a read-write transaction: the rows are locked with
	 * {@code FOR UPDATE SKIP LOCKED} until that transaction ends, so several
	 * scheduler instances can run side by side without picking the same packet.
	 * Keyset paging on {@code (upd_dtimes, workflow_instance_id)} lets the caller
	 * move past packets that fail permanently instead of re-reading them forever.
	 * Only packets created in {@code [createdFrom, createdToExclusive)} (by
	 * {@code cr_dtimes}) are returned.
	 * <p>
	 * Each row is {@code [workflow_instance_id, reg_id, process, status_code,
	 * reg_stage_name, upd_dtimes]}.
	 */
	@Query(value = "SELECT r.workflow_instance_id, r.reg_id, r.process, r.status_code, r.reg_stage_name, r.upd_dtimes "
			+ "FROM registration r WHERE r.upd_dtimes < CURRENT_DATE AND r.is_anonymous_profile_added IS NOT TRUE "
			+ "AND r.cr_dtimes >= :createdFrom AND r.cr_dtimes < :createdToExclusive "
			+ "AND (r.upd_dtimes, r.workflow_instance_id) > (:afterUpdDtimes, :afterWorkflowInstanceId) "
			+ "ORDER BY r.upd_dtimes, r.workflow_instance_id LIMIT :fetchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
	public List<Object[]> getAnonymousNotAddedPacketsForUpdate(@Param("afterUpdDtimes") java.sql.Timestamp afterUpdDtimes,
			@Param("afterWorkflowInstanceId") String afterWorkflowInstanceId,
			@Param("createdFrom") java.sql.Timestamp createdFrom,
			@Param("createdToExclusive") java.sql.Timestamp createdToExclusive, @Param("fetchSize") Integer fetchSize);

	/**
	 * Flags packets as having their anonymous profile added. Only the flag (and
	 * updated-by) is written, so no other column of the row is touched.
	 */
	@Modifying
	@Query(value = "UPDATE registration SET is_anonymous_profile_added = true, upd_by = :updatedBy "
			+ "WHERE workflow_instance_id IN (:workflowInstanceIds)", nativeQuery = true)
	public int markAnonymousProfileAdded(@Param("workflowInstanceIds") List<String> workflowInstanceIds,
			@Param("updatedBy") String updatedBy);
	
	@Query("SELECT registration.registrationType FROM RegistrationStatusEntity registration WHERE registration.regId in :regIds")
	public List<String> getProcessForRegIds(@Param("regIds") List<String> regIds);

	@Query("SELECT registration FROM RegistrationStatusEntity registration WHERE registration.regId = :regId AND registration.registrationType = :registrationType AND registration.iteration = :iteration")
	public List<RegistrationStatusEntity> getByIdAndProcessAndIteration(@Param("regId") String regId, @Param("registrationType") String process, @Param("iteration") int iteration);

	@Query("SELECT registration.referenceId FROM RegistrationStatusEntity registration WHERE registration.regId = :regId AND registration.isDeleted = false AND registration.isActive = true")
	public Optional<String> getReferenceIdByRegId(@Param("regId") String regId);

	@Query("SELECT new map(registration.regId as regId, registration.statusCode as statusCode, registration.latestTransactionTimes as latestTransactionTimes) "
			+ "FROM RegistrationStatusEntity registration WHERE registration.referenceId = :hashedNationalRefId "
			+ "AND registration.regId != :excludeRegId AND registration.registrationType IN :registrationTypes "
			+ "AND registration.latestTransactionTimes >= :latestTransactionTimes AND registration.isDeleted = false AND registration.isActive = true")
	public List<Map<String, Object>> getRegIdAndStatusByReferenceId(@Param("hashedNationalRefId") String hashedNationalRefId, @Param("excludeRegId") String excludeRegId, @Param("registrationTypes") List<String> registrationTypes, @Param("latestTransactionTimes") LocalDateTime latestTransactionTimes);

	@Query("SELECT registration FROM RegistrationStatusEntity registration "
			+ "WHERE registration.regId LIKE :ridPrefix "
			+ "AND registration.statusCode NOT IN :statusCodes "
			+ "AND registration.isDeleted = false AND registration.isActive = true")
	public List<RegistrationStatusEntity> findByRidPrefixExcludingStatusCodes(@Param("ridPrefix") String ridPrefix, @Param("statusCodes") List<String> statusCodes);

	@Query("SELECT registration FROM RegistrationStatusEntity registration WHERE registration.regId IN :regIds")
	List<RegistrationStatusEntity> getStatusAndProcessForRegIds(@Param("regIds") List<String> regIds);
}


