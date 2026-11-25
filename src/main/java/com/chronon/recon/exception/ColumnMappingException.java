package com.chronon.recon.exception;

/** Thrown when required column headers cannot be matched against any registered profile. */
public class ColumnMappingException extends ReconciliationException {

    private static final String DEFAULT_HINT =
            "The column-mapping profile did not recognise the headers. Add the bank's header names "
                    + "as aliases in src/main/resources/profiles/ and try again.";

    public ColumnMappingException(String message) {
        super(message, DEFAULT_HINT);
    }

    public ColumnMappingException(String message, String hint) {
        super(message, hint);
    }
}
