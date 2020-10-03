package com.quaso.validation.exception;

public class ConfigValidationException extends RuntimeException {

    public ConfigValidationException(final String message) {
        super(message);
    }

    public ConfigValidationException(final String message, final ConfigValidationException ex) {
        super(message, ex);
    }
}
