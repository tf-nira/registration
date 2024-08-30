package io.mosip.registration.processor.paymentvalidator.dto;

import java.io.Serializable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class PrnPaymentStatusDTO implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String prnNumber;
	private String prnStatusCode;
	private String prnStatusDesc;
	private String prnTaxHead;
	private String prnTaxPayerName;
}
