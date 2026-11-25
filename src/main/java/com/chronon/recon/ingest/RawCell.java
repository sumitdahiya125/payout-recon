package com.chronon.recon.ingest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One spreadsheet cell, with the value kept in its most useful form.
 *
 * <p>Keeping the raw text as well as the typed value matters: a numeric cell may be formatted as a
 * date, and a text cell may contain a number with a currency symbol. The parsers decide which
 * representation to trust.</p>
 */
public record RawCell(String text, BigDecimal number, LocalDate date) {

    private static final RawCell BLANK = new RawCell("", null, null);

    public static RawCell blank() {
        return BLANK;
    }

    public static RawCell ofText(String text) {
        if (text == null || text.isBlank()) {
            return BLANK;
        }
        return new RawCell(text, null, null);
    }

    public static RawCell ofNumber(BigDecimal number, String text) {
        if (number == null) {
            return ofText(text);
        }
        return new RawCell(text == null ? "" : text, number, null);
    }

    public static RawCell ofDate(LocalDate date) {
        return new RawCell(date == null ? "" : date.toString(), null, date);
    }

    public boolean isBlank() {
        return (text == null || text.isBlank()) && number == null && date == null;
    }

    public String asText() {
        if (text != null && !text.isBlank()) {
            return text;
        }
        if (date != null) {
            return date.toString();
        }
        if (number != null) {
            return number.toPlainString();
        }
        return "";
    }

    public Optional<BigDecimal> asNumber() {
        return Optional.ofNullable(number);
    }

    public Optional<LocalDate> asDate() {
        return Optional.ofNullable(date);
    }
}
