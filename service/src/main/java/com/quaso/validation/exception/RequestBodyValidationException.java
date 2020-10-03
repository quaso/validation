package com.quaso.validation.exception;

import java.util.Collection;
import java.util.stream.Collectors;
import lombok.Value;

@Value
public class RequestBodyValidationException extends RuntimeException {

    private final Collection<ValidationErrorResponse> bodyParamErrors;

    public RequestBodyValidationException(final Collection<ValidationErrorResponse> bodyParamErrors) {
        super(buildMessage(bodyParamErrors));
        this.bodyParamErrors = bodyParamErrors;
    }

    private static String buildMessage(final Collection<ValidationErrorResponse> bodyParamErrors) {
        return bodyParamErrors.stream()
            .map(ValidationErrorResponse::getFullMessage)
            .collect(Collectors.joining(", "));
    }
}
