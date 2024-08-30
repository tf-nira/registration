package io.mosip.registration.processor.paymentvalidator.constants;

public enum PrnStatusCode {

	PRN_STATUS_AVAILABLE("A", "AVAILABLE"),
	PRN_STATUS_RECEIVED_CREDITED("T", "RECEIVED AND CREDITED"),
	PRN_STATUS_RECEIVED_NOT_CREDITED("R", "RECEIVED BUT NOT CREDITED"),
	PRN_STATUS_RECEIVED_BUT_DISHONOURED("D", "RECEIVED BUT DISHONOURED"),
	PRN_STATUS_CANCELLED("C", "CANCELLED"),
	PRN_STATUS_EXPIRED("X", "EXPIRED");
	
	final String statusCode;
	private final String statusDesc;


	private PrnStatusCode(String statusCode, String statusDesc) {
		this.statusCode = statusCode;
		this.statusDesc = statusDesc;
	}


	public String getStatusCode() {
		return statusCode;
	}

	public String getStatusDesc() {
		return statusDesc;
	}
}
