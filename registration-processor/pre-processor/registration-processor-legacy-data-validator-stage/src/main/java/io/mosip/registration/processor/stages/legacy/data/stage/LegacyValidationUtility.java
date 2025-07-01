package io.mosip.registration.processor.stages.legacy.data.stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.exception.ValidationFailedException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.packet.storage.utils.PriorityBasedPacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;


@Service
public class LegacyValidationUtility {
	private static Logger regProcLogger = RegProcessorLogger.getLogger(LegacyValidationUtility.class);

	/** Tag name that will be used while tagging age group */
	@Value("${mosip.regproc.packet.classifier.tagging.agegroup.tag-name:AGE_GROUP}")
	private String tagName;

	/**
	 * Below age ranges map should contain proper age group name and age range, any
	 * overlap of the age range will result in a random behaviour of tagging. In
	 * range, upper and lower values are inclusive.
	 */
	@Value("#{${mosip.regproc.packet.classifier.tagging.agegroup.ranges:{'CHILD':'0-17','ADULT':'18-59','SENIOR_CITIZEN':'60-200'}}}")
	private Map<String, String> ageGroupRangeMap;

	/**
	 * The tag value that will be used by default when the packet does not have
	 * value for the tag field
	 */
	@Value("${mosip.regproc.packet.classifier.tagging.not-available-tag-value}")
	private String notAvailableTagValue;

	/** Frequently used util methods are available in this bean */
	@Autowired
	private Utilities utility;
	
	@Autowired
	private PriorityBasedPacketManagerService packetManagerService;

	private static String RANGE_DELIMITER = "-";

	private Map<String, int[]> parsedAgeGroupRangemap;

	@Value("${mosip.regproc.introducer-validator.renewal.age.limit:16}")
	private String RenewalAgelimit;

	@Value("${mosip.regproc.packet.validator.max.number.spouses:4}")
	private Integer maxNumberOfSpouses;

	@PostConstruct
	private void generateParsedAgeGroupRangeMap() {
		parsedAgeGroupRangemap = new HashMap<>();
		for (Map.Entry<String, String> entry : ageGroupRangeMap.entrySet()) {
			String[] range = entry.getValue().split(RANGE_DELIMITER);
			int[] rangeArray = new int[2];
			rangeArray[0] = Integer.parseInt(range[0]);
			rangeArray[1] = Integer.parseInt(range[1]);
			parsedAgeGroupRangemap.put(entry.getKey(), rangeArray);
		}
	}


	/**
	 * {@inheritDoc}
	 * 
	 * @throws IOException
	 * @throws PacketManagerException
	 * @throws JsonProcessingException
	 * @throws ApisResourceAccessException
	 * @throws ValidationFailedException
	 */

	public Map<String, String> generateAgeTags(String registrationId, String process)
			throws IOException, ApisResourceAccessException, JsonProcessingException, PacketManagerException,
			ValidationFailedException {

			String ageGroup = "";
			int age = utility.getApplicantAge(registrationId, process, ProviderStageName.CLASSIFICATION);

			if (age == -1) {
				ageGroup = notAvailableTagValue;
			} else {
				for (Map.Entry<String, int[]> entry : parsedAgeGroupRangemap.entrySet()) {
					if (age >= entry.getValue()[0] && age <= entry.getValue()[1]) {
						ageGroup = entry.getKey();
						break;
					}
				}
			}

			if (ageGroup == null || ageGroup.trim().isEmpty())
				throw new ValidationFailedException(PlatformErrorMessages.RPR_PCM_AGE_GROUP_NOT_FOUND.getCode(),
						PlatformErrorMessages.RPR_PCM_AGE_GROUP_NOT_FOUND.getMessage() + " Age: " + age);

			Map<String, String> tags = new HashMap<String, String>();
			tags.put(tagName, ageGroup);
			return tags;

	}

	public boolean checkNumberOfSpouses(JSONObject jsonObject, String id, String process)
			throws ApisResourceAccessException, PacketManagerException, JsonProcessingException, IOException {
		boolean isValidNumberOfSpouse = true;
		String numberOfOtherSpousesInDb = JsonUtil.getJSONValue(jsonObject, MappingJsonConstants.NUMBEROFOTHERSPOUSES);
		if (numberOfOtherSpousesInDb != null) {
			int numberOfOtherSpousesInDbValue = Integer.parseInt(numberOfOtherSpousesInDb);
			String numberOfOtherSpousesInPacket = packetManagerService.getFieldByMappingJsonKey(id,
					MappingJsonConstants.NUMBEROFOTHERSPOUSES, process, ProviderStageName.PACKET_VALIDATOR);
			if (numberOfOtherSpousesInPacket != null) {
				int numberOfOtherSpousesInPacketValue = Integer.parseInt(numberOfOtherSpousesInPacket);
				if (numberOfOtherSpousesInDbValue < maxNumberOfSpouses) {
					int leftOutSpouses = maxNumberOfSpouses - numberOfOtherSpousesInDbValue;
					if (numberOfOtherSpousesInPacketValue > leftOutSpouses) {
						isValidNumberOfSpouse = false;
					}
				} else if (numberOfOtherSpousesInDbValue >= maxNumberOfSpouses) {
					isValidNumberOfSpouse = false;
				}
			}
		}
		return isValidNumberOfSpouse;
	}

	public boolean validateAgeToRenewal(String id, String process)
			throws ApisResourceAccessException, JsonProcessingException, PacketManagerException, IOException {
		int age = utility.getApplicantAge(id, process, ProviderStageName.PACKET_VALIDATOR);
		int ageThreshold = Integer.parseInt(RenewalAgelimit);
		if (age < ageThreshold) {

			return false;
		}
		return true;

	}
}
