package io.mosip.registration.processor.paymentvalidator.dto;

import java.io.Serializable;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrnsConsumedListMetaDTO implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private List<PrnsConsumedListViewDTO> prns;
	private String totalRecords;

}
