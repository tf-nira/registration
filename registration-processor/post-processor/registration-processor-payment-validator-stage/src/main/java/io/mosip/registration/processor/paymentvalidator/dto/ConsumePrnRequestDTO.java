package io.mosip.registration.processor.paymentvalidator.dto;

import java.io.Serializable;

import org.json.simple.JSONObject;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class ConsumePrnRequestDTO implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private String prn;
	private String regId;

}