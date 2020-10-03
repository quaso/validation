package com.quaso.validation.exception;

import lombok.Getter;

@Getter
public class UnknownFieldException extends RuntimeException {

    public UnknownFieldException(final String fieldName, final Class<?> clazz) {
        super("Cannot find field '" + fieldName + "' in class " + clazz.getCanonicalName());
    }

    public UnknownFieldException(final String fieldName) {
        super("Cannot find field '" + fieldName + "' in any parent class");
    }
}
