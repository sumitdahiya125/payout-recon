package com.chronon.recon.ingest.normalizer;

import com.chronon.recon.domain.Money;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the many ways a bank or an export tool writes an amount.
 *
 * <p>Handles currency prefixes (Rs, INR, the rupee sign), thousands separators (both 1,234.50 and
 * the European 1.234,50), accounting negatives "(1,200.00)", trailing minus, and Dr/Cr suffixes.
 * Dr is treated as a negative value and Cr as positive; callers that know a column is a debit
 * column take the absolute value.</p>
 */
public final class MoneyParser {

    private static final Pattern CURRENCY = Pattern.compile("(?i)\\b(inr|rs|rupees?|usd)\\b|[₹$]");
    private static final Pattern DR_SUFFIX = Pattern.compile("(?i)(dr|debit)\\.?$");
    private static final Pattern CR_SUFFIX = Pattern.compile("(?i)(cr|credit)\\.?$");
    private static final Pattern EUROPEAN = Pattern.compile("\\d{1,3}(\\.\\d{3})+(,\\d{1,2})?");
    private static final Pattern NUMERIC = Pattern.compile("[0-9.]+");

    private MoneyParser() {
    }

    public static Optional<Money> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = TextNormalizer.clean(raw);
        if (value.isEmpty()) {
            return Optional.empty();
        }

        value = value.replaceAll("[\\s\\u00A0]", "");

        boolean negative = false;
        if (value.startsWith("(") && value.endsWith(")")) {
            negative = true;
            value = value.substring(1, value.length() - 1);
        }
        if (value.endsWith("-")) {
            negative = true;
            value = value.substring(0, value.length() - 1);
        }
        if (value.startsWith("-") || value.startsWith("\u2212")) {
            negative = true;
            value = value.substring(1);
        }
        if (value.startsWith("+")) {
            value = value.substring(1);
        }

        Matcher dr = DR_SUFFIX.matcher(value);
        if (dr.find()) {
            negative = true;
            value = value.substring(0, dr.start());
        }
        Matcher cr = CR_SUFFIX.matcher(value);
        if (cr.find()) {
            value = value.substring(0, cr.start());
        }

        value = CURRENCY.matcher(value).replaceAll("");
        value = value.replaceAll("(?i)(inr|rs|usd)", "");
        value = value.replaceAll("[A-Za-z₹$]", "");
        value = value.replaceAll("^\\.|\\.$", "");

        if (value.isEmpty()) {
            return Optional.empty();
        }

        if (EUROPEAN.matcher(value).matches()) {
            value = value.replace(".", "").replace(',', '.');
        } else {
            value = value.replace(",", "");
        }

        if (!NUMERIC.matcher(value).matches()) {
            return Optional.empty();
        }

        try {
            BigDecimal amount = new BigDecimal(value);
            if (negative) {
                amount = amount.negate();
            }
            return Optional.of(Money.of(amount));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    /** True when the raw text carries an explicit negative marker, useful for diagnostics. */
    public static boolean looksNegative(String raw) {
        if (raw == null) {
            return false;
        }
        String value = TextNormalizer.clean(raw).replaceAll("\\s", "");
        return value.startsWith("(") || value.startsWith("-") || value.endsWith("-")
                || DR_SUFFIX.matcher(value).find();
    }
}
