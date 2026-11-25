package com.chronon.recon.exception;

/** Thrown when uploaded files fail validation (e.g., missing file, unsupported extension). */
public class ValidationException extends ReconciliationException {

    public ValidationException(String message, String hint) {
        super(message, hint);
    }
}
