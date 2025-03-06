package io.mosip.registration.processor.core.exception;

import io.mosip.kernel.core.exception.BaseCheckedException;

public class LegacyDataValidationException extends BaseCheckedException {

	private static final long serialVersionUID = 1L;

	public LegacyDataValidationException(String errorCode, String message) {
		super(errorCode, message);
	}

	public LegacyDataValidationException(String errorCode, String message, Throwable t) {
		super(errorCode, message, t);
	}
}
