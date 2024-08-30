package io.mosip.registration.processor.paymentvalidator.dto;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrnsConsumedListViewDTO implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private Long prnConsumedId;
	private String prnConsumedNumber;
	private String regId;
}
