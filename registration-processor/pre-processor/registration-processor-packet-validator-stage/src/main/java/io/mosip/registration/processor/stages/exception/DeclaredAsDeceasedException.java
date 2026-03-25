package io.mosip.registration.processor.stages.exception;

import io.mosip.registration.processor.core.exception.RegistrationProcessorCheckedException;

public class DeclaredAsDeceasedException extends RegistrationProcessorCheckedException {

    private static final long serialVersionUID = 1L;

    public DeclaredAsDeceasedException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }
}
