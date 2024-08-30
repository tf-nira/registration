package io.mosip.registration.processor.paymentvalidator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class PrnStatusResponseDataDTO {

	@JsonProperty("AmountPaid")
	private String amountPaid;
	
	@JsonProperty("Currency")
	private String currency;
	
	@JsonProperty("DatePaid")
	private String datePaid;
	
	@JsonProperty("MDAName")
	private String mdaName;
	
	@JsonProperty("PRN")
	private String prn;
	
	@JsonProperty("PaymentBank")
	private String paymentBank;
	
	@JsonProperty("PaymentMode")
	private String paymentMode;
	
	@JsonProperty("RealizationDate")
	private String realizationDate;
	
	@JsonProperty("ReferenceNumber")
	private String referenceNumber;
	
	@JsonProperty("StatusCode")
	private String statusCode;
	
	@JsonProperty("StatusDesc")
	private String statusDesc;
	
	@JsonProperty("TIN")
	private String tin;
	
	@JsonProperty("TaxHeadCode")
	private String taxHeadCode;
	
	@JsonProperty("TaxHeadName")
	private String taxHeadName;
	
	@JsonProperty("TaxPayerEmail")
	private String taxPayerEmail;
	
	@JsonProperty("TaxPayerName")
	private String taxPayerName;
	
	@JsonProperty("SearchCode")
	private String searchCode;
	
	@JsonProperty("ProcessFlow")
	private String processFlow;
}
