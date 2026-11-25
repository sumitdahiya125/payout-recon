package com.chronon.recon.exception;

/** Thrown when a workbook cannot be read, parsed, or exceeds supported capacity. */
public class IngestionException extends ReconciliationException {

    private static final String DEFAULT_HINT =
            "Ensure the file is a valid Excel workbook (.xlsx or .xls) and contains readable data rows.";

    public IngestionException(String message) {
        super(message, DEFAULT_HINT);
    }

    public IngestionException(String message, Throwable cause) {
        super(message, DEFAULT_HINT, cause);
    }

    public IngestionException(String message, String hint) {
        super(message, hint);
    }

    public IngestionException(String message, String hint, Throwable cause) {
        super(message, hint, cause);
    }
}
