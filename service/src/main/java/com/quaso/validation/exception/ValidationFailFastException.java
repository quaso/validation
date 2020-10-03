package com.quaso.validation.exception;

import lombok.Getter;

@Getter
public class ValidationFailFastException extends Exception {

    private final ValidationErrorResponse validationErrorResponse;

    public ValidationFailFastException(final ValidationErrorResponse validationErrorResponse) {
        super(validationErrorResponse.getFullMessage());
        this.validationErrorResponse = validationErrorResponse;
    }
}
