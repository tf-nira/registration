package io.mosip.registration.processor.paymentvalidator.constants;

/**
 * 
 * 
 * @author Ibrahim Nkambo
 */

public enum TaxHeadCode {
	
	TAX_HEAD_REPLACE,
    TAX_HEAD_CHANGE,
    TAX_HEAD_CORRECTION_ERRORS,
    TAX_HEAD_REPLACE_DEFACED;
	
	private String taxHeadCode;
	private String taxHeadDesc;
	private String amountPaid;


	/*private TaxHeadCode(String taxHeadCode, String taxHeadDesc, String amountPaid) {
		this.taxHeadCode = taxHeadCode;
		this.taxHeadDesc = taxHeadDesc;
		this.amountPaid = amountPaid;
	}*/


	public String getTaxHeadDesc() {
		return taxHeadDesc;
	}

	public String getTaxHeadCode() {
		return taxHeadCode;
	}
	
	public String getAmountPaid() {
		return amountPaid;
	}


	public void setValues(String taxHeadCode, String taxHeadDesc, String amountPaid) {
        this.taxHeadCode = taxHeadCode;
        this.taxHeadDesc = taxHeadDesc;
        this.amountPaid = amountPaid;
    }


}
