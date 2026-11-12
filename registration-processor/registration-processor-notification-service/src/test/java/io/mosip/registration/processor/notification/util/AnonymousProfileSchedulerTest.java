package io.mosip.registration.processor.notification.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.packet.storage.utils.IdSchemaUtil;
import io.mosip.registration.processor.packet.storage.utils.PacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.status.dao.RegistrationStatusDao;
import io.mosip.registration.processor.status.entity.AnonymousProfileEntity;
import io.mosip.registration.processor.status.service.AnonymousProfileService;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AnonymousProfileSchedulerTest {

	private static final Timestamp T1 = Timestamp.valueOf("2026-11-01 10:00:00");
	private static final Timestamp T2 = Timestamp.valueOf("2026-11-01 10:00:01");
	private static final Timestamp T3 = Timestamp.valueOf("2026-11-01 10:00:02");

	@Mock
	private RegistrationStatusDao registrationStatusDao;

	@Mock
	private Utilities utility;

	@Mock
	private IdSchemaUtil idSchemaUtil;

	@Mock
	private PacketManagerService packetManagerService;

	@Mock
	private AnonymousProfileService anonymousProfileService;

	@Mock
	private PlatformTransactionManager transactionManager;

	@InjectMocks
	private AnonymousProfileScheduler scheduler;

	@Before
	public void setUp() throws Exception {
		LinkedHashMap<String, String> idSchemaVersion = new LinkedHashMap<>();
		idSchemaVersion.put("value", "IDSchemaVersion");
		JSONObject identity = new JSONObject();
		identity.put(MappingJsonConstants.IDSCHEMA_VERSION, idSchemaVersion);
		when(utility.getRegistrationProcessorMappingJson(MappingJsonConstants.IDENTITY)).thenReturn(identity);
		when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

		ReflectionTestUtils.setField(scheduler, "fetchSize", 3);
		ReflectionTestUtils.setField(scheduler, "numberOfThreads", 2);
		ReflectionTestUtils.setField(scheduler, "anonymousProfileBioInfoRequired", false);
		ReflectionTestUtils.setField(scheduler, "maxRunSeconds", 30);
		ReflectionTestUtils.setField(scheduler, "insertBatchSize", 500);
		scheduler.init();

		when(packetManagerService.getFieldByMappingJsonKey(anyString(), any(), any(), any())).thenReturn("1.0");
		when(idSchemaUtil.getIdSchemaFieldTypes(any())).thenReturn(new HashMap<String, String>());
		when(idSchemaUtil.getDefaultFields(any())).thenReturn(new ArrayList<String>());
		when(packetManagerService.getFields(anyString(), any(), any(), any())).thenReturn(new HashMap<String, String>());
		when(packetManagerService.getMetaInfo(anyString(), any(), any())).thenReturn(new HashMap<String, String>());
		when(anonymousProfileService.buildJsonStringFromPacketInfo(any(), any(), any(), any(), any(), any()))
				.thenReturn("{}");
	}

	@After
	public void tearDown() {
		scheduler.shutdown();
	}

	private static Object[] row(String workflowInstanceId, String regId, Timestamp updDtimes, String stage) {
		return new Object[] { workflowInstanceId, regId, "NEW", "PROCESSED", stage, updDtimes };
	}

	@SuppressWarnings("unchecked")
	private List<String> flaggedIds() {
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(registrationStatusDao, times(1)).markAnonymousProfileAdded(captor.capture(), anyString());
		return captor.getValue();
	}

	@Test
	public void insertsProfilesAndFlagsOnlyThePacketsThatSucceeded() throws Exception {
		List<Object[]> chunk = Arrays.asList(row("wf1", "RID1", T1, "PacketValidatorStage"),
				row("wf2", "RID2", T2, "PacketValidatorStage"), row("wf3", "RID3", T3, "PacketValidatorStage"));
		when(registrationStatusDao.getAnonymousNotAddedPacketsForUpdate(any(), anyString(), anyInt()))
				.thenReturn(chunk).thenReturn(Collections.<Object[]>emptyList());
		// the second packet cannot be read
		when(packetManagerService.getFieldByMappingJsonKey(eq("RID2"), any(), any(), any()))
				.thenThrow(new RuntimeException("packet not found"));

		scheduler.runJob();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<AnonymousProfileEntity>> inserted = ArgumentCaptor.forClass(List.class);
		verify(anonymousProfileService, times(1)).saveAnonymousProfilesBatch(inserted.capture(), eq(500));
		assertEquals(2, inserted.getValue().size());
		assertEquals(Arrays.asList("wf1", "wf3"), flaggedIds());
	}

	@Test
	public void secondChunkStartsAfterTheLastPacketOfTheFirstChunk() {
		List<Object[]> chunk = Arrays.asList(row("wf1", "RID1", T1, "PacketValidatorStage"),
				row("wf2", "RID2", T2, "PacketValidatorStage"), row("wf3", "RID3", T3, "PacketValidatorStage"));
		when(registrationStatusDao.getAnonymousNotAddedPacketsForUpdate(any(), anyString(), anyInt()))
				.thenReturn(chunk).thenReturn(Collections.<Object[]>emptyList());

		scheduler.runJob();

		verify(registrationStatusDao).getAnonymousNotAddedPacketsForUpdate(Timestamp.valueOf("1970-01-01 00:00:00"),
				"", 3);
		verify(registrationStatusDao).getAnonymousNotAddedPacketsForUpdate(T3, "wf3", 3);
		verify(registrationStatusDao, times(2)).getAnonymousNotAddedPacketsForUpdate(any(), anyString(), anyInt());
	}

	@Test
	public void packetsThatAlwaysFailDoNotBlockTheOnesBehindThem() throws Exception {
		List<Object[]> failing = Arrays.asList(row("wf1", "RID1", T1, "PacketValidatorStage"),
				row("wf2", "RID2", T2, "PacketValidatorStage"));
		List<Object[]> healthy = Arrays.<Object[]>asList(row("wf3", "RID3", T3, "PacketValidatorStage"));
		when(registrationStatusDao.getAnonymousNotAddedPacketsForUpdate(any(), anyString(), anyInt()))
				.thenReturn(failing).thenReturn(healthy).thenReturn(Collections.<Object[]>emptyList());
		when(packetManagerService.getFieldByMappingJsonKey(eq("RID1"), any(), any(), any()))
				.thenThrow(new RuntimeException("packet not found"));
		when(packetManagerService.getFieldByMappingJsonKey(eq("RID2"), any(), any(), any()))
				.thenThrow(new RuntimeException("packet not found"));

		scheduler.runJob();

		// nothing from the failing chunk is written or flagged, the healthy packet is
		assertEquals(Arrays.asList("wf3"), flaggedIds());
		verify(anonymousProfileService, times(1)).saveAnonymousProfilesBatch(anyList(), anyInt());
	}

	@Test
	public void packetWithoutStageNameIsLeftForALaterRun() {
		List<Object[]> chunk = Arrays.<Object[]>asList(row("wf1", "RID1", T1, null));
		when(registrationStatusDao.getAnonymousNotAddedPacketsForUpdate(any(), anyString(), anyInt()))
				.thenReturn(chunk).thenReturn(Collections.<Object[]>emptyList());

		scheduler.runJob();

		verify(anonymousProfileService, never()).saveAnonymousProfilesBatch(anyList(), anyInt());
		verify(registrationStatusDao, never()).markAnonymousProfileAdded(anyList(), anyString());
	}

	@Test
	public void failedInsertRollsTheChunkBackAndTheRunMovesOn() {
		List<Object[]> bad = Arrays.<Object[]>asList(row("wf1", "RID1", T1, "PacketValidatorStage"));
		List<Object[]> good = Arrays.<Object[]>asList(row("wf2", "RID2", T2, "PacketValidatorStage"));
		when(registrationStatusDao.getAnonymousNotAddedPacketsForUpdate(any(), anyString(), anyInt()))
				.thenReturn(bad).thenReturn(good).thenReturn(Collections.<Object[]>emptyList());
		org.mockito.Mockito.doThrow(new RuntimeException("insert failed")).doNothing().when(anonymousProfileService)
				.saveAnonymousProfilesBatch(anyList(), anyInt());

		scheduler.runJob();

		// first chunk: insert failed, so the flag update never ran and the transaction was rolled back
		verify(transactionManager, times(1)).rollback(any());
		assertEquals(Arrays.asList("wf2"), flaggedIds());
	}

	@Test
	public void runStopsAfterRepeatedChunkFailures() {
		when(registrationStatusDao.getAnonymousNotAddedPacketsForUpdate(any(), anyString(), anyInt()))
				.thenThrow(new RuntimeException("database unavailable"));

		scheduler.runJob();

		verify(registrationStatusDao, times(3)).getAnonymousNotAddedPacketsForUpdate(any(), anyString(), anyInt());
		verify(anonymousProfileService, never()).saveAnonymousProfilesBatch(anyList(), anyInt());
	}

	@Test
	public void nothingPendingDoesNotWriteAnything() {
		when(registrationStatusDao.getAnonymousNotAddedPacketsForUpdate(any(), anyString(), anyInt()))
				.thenReturn(Collections.<Object[]>emptyList());

		scheduler.runJob();

		verify(anonymousProfileService, never()).saveAnonymousProfilesBatch(anyList(), anyInt());
		verify(registrationStatusDao, never()).markAnonymousProfileAdded(anyList(), anyString());
		assertTrue(true);
	}
}
