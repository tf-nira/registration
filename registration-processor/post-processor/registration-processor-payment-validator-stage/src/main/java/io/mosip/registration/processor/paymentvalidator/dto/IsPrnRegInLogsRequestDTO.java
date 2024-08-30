package io.mosip.registration.processor.paymentvalidator.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class IsPrnRegInLogsRequestDTO implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private String prn;
	private String regId;

}
