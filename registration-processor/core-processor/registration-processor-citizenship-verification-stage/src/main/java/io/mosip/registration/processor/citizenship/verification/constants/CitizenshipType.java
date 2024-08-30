package io.mosip.registration.processor.citizenship.verification.constants;

public enum CitizenshipType {
	BIRTH("By Birth /Descent"),
	NATURALISATION("By Naturalisation"),
	REGISTRATION("By Registration");
	
	final String citizenshipType;

	private CitizenshipType(String citizenshipType) {
		this.citizenshipType = citizenshipType;

	}
	
	public String getCitizenshipType() {
		return citizenshipType;
	}
}
