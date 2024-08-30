package io.mosip.registration.processor.citizenship.verification.constants;

public enum StatusForNinandLivivngStatus {
	ALIVE("Alive"),
    DECEASED("Deceased"),
	ACTIVATED("ACTIVATED"),
	DEACTIVATED("DEACTIVATED");

    private final String status;

    private StatusForNinandLivivngStatus(String status) {
    	this.status = status;
    }
 
    public String getStatus() {
        return this.status;
    }
    
}
