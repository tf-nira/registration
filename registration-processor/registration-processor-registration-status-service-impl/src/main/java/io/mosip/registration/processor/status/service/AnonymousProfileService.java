package io.mosip.registration.processor.status.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.springframework.stereotype.Service;

import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.status.entity.AnonymousProfileEntity;

@Service
public interface AnonymousProfileService {

	/**
	 * save anonymous profile
	 * @param id
	 * @param processStage
	 * @param profileJson
	 */
	public void saveAnonymousProfile(String regId, String processStage,String profileJson);

	public String buildJsonStringFromPacketInfo(BiometricRecord biometricRecord, Map<String, String> fieldMap,
			Map<String, String> fieldTypeMap, Map<String, String> metaInfoMap, String statusCode, String processStage)
			throws JSONException, ApisResourceAccessException, PacketManagerException, IOException, BaseCheckedException;
	
	public void saveAnonymousProfiles(List<AnonymousProfileEntity> anonymousProfiles);

	/**
	 * Inserts the profiles with plain JDBC batching inside the current
	 * transaction. Unlike {@link #saveAnonymousProfiles(List)} it never issues a
	 * per-row SELECT (the id is assigned by the application, so Spring Data would
	 * otherwise treat the entity as existing and merge it).
	 *
	 * @param anonymousProfiles the profiles to insert
	 * @param batchSize         rows sent to the database per JDBC batch
	 */
	public void saveAnonymousProfilesBatch(List<AnonymousProfileEntity> anonymousProfiles, int batchSize);
}
