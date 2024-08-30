package io.mosip.registration.processor.paymentvalidator.dto;

import java.io.Serializable;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Setter
@Getter
public class IsPrnRegInLogsResponseDTO implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private String prn;
	private String regId;
	private boolean isPresentInLogs;

}
