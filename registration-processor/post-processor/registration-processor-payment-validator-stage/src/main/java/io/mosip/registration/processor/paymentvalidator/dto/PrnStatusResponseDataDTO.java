package io.mosip.registration.processor.paymentvalidator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class PrnStatusResponseDataDTO {

	@JsonProperty("amountPaid")
	private String amountPaid;
	
	@JsonProperty("currency")
	private String currency;
	
	@JsonProperty("datePaid")
	private String datePaid;
	
	@JsonProperty("mdaName")
	private String mdaName;
	
	@JsonProperty("prn")
	private String prn;
	
	@JsonProperty("paymentBank")
	private String paymentBank;
	
	@JsonProperty("paymentMode")
	private String paymentMode;
	
	@JsonProperty("realizationDate")
	private String realizationDate;
	
	@JsonProperty("referenceNumber")
	private String referenceNumber;
	
	@JsonProperty("statusCode")
	private String statusCode;
	
	@JsonProperty("statusDesc")
	private String statusDesc;
	
	@JsonProperty("tin")
	private String tin;
	
	@JsonProperty("taxHeadCode")
	private String taxHeadCode;
	
	@JsonProperty("taxHeadName")
	private String taxHeadName;
	
	@JsonProperty("taxPayerEmail")
	private String taxPayerEmail;
	
	@JsonProperty("taxPayerName")
	private String taxPayerName;
	
	@JsonProperty("searchCode")
	private String searchCode;
	
	@JsonProperty("processFlowPaidFor")
	private String processFlow;

	@JsonProperty("subServiceTypePaidFor")
	private String subServiceTypePaidFor;
}
