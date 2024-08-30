package io.mosip.registration.processor.paymentvalidator.dto;

/**
 * This is a dto class for the main overall response that imitates the MOSIP response object
 * 
 * @reference mosip.io
 * @author ibrahim.nkambo
 */
import java.io.Serializable;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class MainMosipResponseDTO<T> implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private String id;
	private String version;
	private String responsetime;
	private T response;
	
	/** The error details. */
	private List<ExceptionJSONInfoDTO> errors;
}
