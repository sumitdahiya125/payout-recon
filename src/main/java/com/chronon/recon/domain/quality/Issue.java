package com.chronon.recon.domain.quality;

/**
 * One data-quality observation, traceable back to the spreadsheet row and column that caused it.
 *
 * @param source "Payout register" or "Bank statement"
 * @param row    1-based spreadsheet row, or 0 for file-level observations
 */
public record Issue(Severity severity, String source, int row, String field, String message) {

    public static Issue error(String source, int row, String field, String message) {
        return new Issue(Severity.ERROR, source, row, field, message);
    }

    public static Issue warn(String source, int row, String field, String message) {
        return new Issue(Severity.WARN, source, row, field, message);
    }

    public static Issue info(String source, int row, String field, String message) {
        return new Issue(Severity.INFO, source, row, field, message);
    }

    public String location() {
        return row <= 0 ? source : source + " row " + row;
    }
}
