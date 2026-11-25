package com.chronon.recon.exception;

/**
 * Base abstract runtime exception for all reconciliation business failures.
 *
 * <p>Carries an actionable user hint alongside the message to guide finance users
 * on how to resolve the issue without needing to inspect technical stack traces.</p>
 */
public abstract class ReconciliationException extends RuntimeException {

    private final String hint;

    protected ReconciliationException(String message, String hint) {
        super(message);
        this.hint = hint;
    }

    protected ReconciliationException(String message, String hint, Throwable cause) {
        super(message, cause);
        this.hint = hint;
    }

    public String getHint() {
        return hint;
    }
}
