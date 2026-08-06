package io.mosip.registration.processor.mvs.request.dto;

import lombok.Data;

import java.util.List;

@Data
public class VerificationRequestDTO {
	private String id;
	private String version;
	private String requestId;
	private String requesttime;
	private String regId;
	private String service;
	private String serviceType;
	private String referenceURL;
	private String source;
	private String refId;
	private String schemaVersion;
	private String statusComment;
	private String foundLink;
	private String ageGroup;
	private String applicantPlaceOfResidenceDistrict;
	private List<String> matchedRegIds;
	private String surname;
	private String givenName;
	private String dateOfBirth;
	private String applicantPlaceOfEnrolmentDistrict;
	
	

}
