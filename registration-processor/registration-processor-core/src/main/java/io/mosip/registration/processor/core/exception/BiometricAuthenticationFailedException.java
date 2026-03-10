package io.mosip.registration.processor.core.exception;

/**
 * Exception thrown when biometric authentication fails due to BioSDK processing errors,
 * including JSON deserialization issues when communicating with the BioSDK service.
 */
public class BiometricAuthenticationFailedException extends ValidationFailedException {

    private static final long serialVersionUID = 1L;

    public BiometricAuthenticationFailedException(String errorCode, String message) {
        super(errorCode, message);
    }

    public BiometricAuthenticationFailedException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * Checks if the given exception or any of its causes is a BiometricAuthenticationFailedException
     * or contains a MismatchedInputException (BioSDK JSON deserialization error).
     *
     * @param e the exception to check
     * @return true if the exception chain contains a biometric authentication failure
     */
    public static boolean isBiometricAuthFailure(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof BiometricAuthenticationFailedException) {
                return true;
            }
            if (current instanceof com.fasterxml.jackson.databind.exc.MismatchedInputException) {
                return true;
            }
            // Also check by class name in case of classloader issues
            if (current.getClass().getName().contains("MismatchedInputException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}