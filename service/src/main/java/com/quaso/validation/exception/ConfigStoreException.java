package com.quaso.validation.exception;

public class ConfigStoreException extends Exception {

    public ConfigStoreException(final Exception ex) {
        super(ex);
    }

    public ConfigStoreException(final String message) {
        super(message);
    }
}
