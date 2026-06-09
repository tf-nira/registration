package io.mosip.registration.processor.core.exception;

import io.mosip.kernel.core.exception.BaseCheckedException;

public class AlienOnHoldException extends BaseCheckedException {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new IntroducerValidationException .
     */
    public AlienOnHoldException() {
        super();
    }

    /**
     *
     * @param message
     */
    public AlienOnHoldException(String code,String message) {
        super(code, message);
    }

}