package io.mosip.registration.processor.paymentvalidator.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class PrnStatusResponseDTO{
	
	private String message;
	private int code;
	private PrnStatusResponseDataDTO data;
}
