package io.mosip.registration.processor.paymentvalidator.dto;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@Getter
@Setter
@NoArgsConstructor
@ToString
public class MainMosipRequestDTO<T> implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * Id
	 */
	private String id;
	/**
	 * version
	 */
	private String version;
	/**
	 * Request Date Time
	 */
	
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
	private Date requesttime;
	/**
	 * Request Object
	 */
	private T request;
	
	public Date getRequesttime() {
		return requesttime!=null ? new Date(requesttime.getTime()):null;
	}
	public void setRequesttime(Date requesttime) {
		this.requesttime =requesttime!=null ? new Date(requesttime.getTime()):null;
	}

}
