package io.mosip.registration.processor.stages.legacy.data.val.dto;

import java.util.List;

public class IdentifyPersonRequest {
	 private List<Fingerprint> fingerprints;
	    private String requestId;
	    private String nationalId;

	    // Getters and setters
	    public List<Fingerprint> getFingerprints() {
	        return fingerprints;
	    }

	    public void setFingerprints(List<Fingerprint> fingerprints) {
	        this.fingerprints = fingerprints;
	    }

	    public String getRequestId() {
	        return requestId;
	    }

	    public void setRequestId(String requestId) {
	        this.requestId = requestId;
	    }

	    public String getNationalId() {
	        return nationalId;
	    }

	    public void setNationalId(String nationalId) {
	        this.nationalId = nationalId;
	    }

	    @Override
	    public String toString() {
	        return "IdentifyPersonRequest{" +
	                "fingerprints=" + fingerprints +
	                ", requestId='" + requestId + '\'' +
	                ", nationalId='" + nationalId + '\'' +
	                '}';
	    }

}
