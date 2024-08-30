package io.mosip.registration.processor.paymentvalidator.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class PrnStatusRequestDTO {

	@JsonProperty("PRN")
	private String PRN;
	
}
