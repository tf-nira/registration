package io.mosip.registration.processor.paymentvalidator.dto;

import java.io.Serializable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class ConsumePrnResponseDTO implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private String prnNum;
	private String regId;
	private boolean consumedStatus;

}

