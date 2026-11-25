package com.chronon.recon.ingest.normalizer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Date parsing that is explicit about ambiguity.
 *
 * <p>An Indian bank export can contain both 04/03/2025 (4 March) and 03/04/2025 (3 April) in the
 * same column. Guessing per row produces silently wrong reconciliation, so the caller decides an
 * order for the whole column (see {@link #detectOrder}) and every value is parsed against it.</p>
 */
public final class DateParser {

    private static final Pattern TWO_DIGIT_YEAR =
            Pattern.compile("^(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2})$");
    private static final Pattern AMBIGUOUS_NUMERIC =
            Pattern.compile("^(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4})$");
    private static final Pattern TIME_SUFFIX =
            Pattern.compile("[T ]\\d{1,2}:\\d{2}(:\\d{2})?(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?$");

    private static final List<DateTimeFormatter> DAY_FIRST = List.of(
            strict("d/M/uuuu"), strict("d-M-uuuu"), strict("d.M.uuuu")
    );
    private static final List<DateTimeFormatter> MONTH_FIRST = List.of(
            strict("M/d/uuuu"), strict("M-d-uuuu"), strict("M.d.uuuu")
    );
    private static final List<DateTimeFormatter> TEXTUAL = List.of(
            strict("d MMM uuuu"), strict("d-MMM-uuuu"), strict("d.MMM.uuuu"),
            strict("d MMM, uuuu"), strict("d-MMM, uuuu"),
            strict("MMM d, uuuu"), strict("MMM d uuuu"),
            strict("d MMMM uuuu"), strict("d-MMMM-uuuu"), strict("MMMM d, uuuu")
    );
    private static final List<DateTimeFormatter> ISO = List.of(
            strict("uuuu-M-d"), strict("uuuu/M/d"), strict("uuuu.M.d")
    );

    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    private DateParser() {
    }

    private static DateTimeFormatter strict(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);
    }

    public static Optional<LocalDate> parse(String raw, DateOrder order) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = TextNormalizer.clean(raw);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        value = TIME_SUFFIX.matcher(value).replaceAll("");
        value = value.replaceAll("(?<=\\d)(st|nd|rd|th)\\b", "");
        value = value.replaceAll("\\s+", " ").trim();

        // ISO first: unambiguous, and the most common machine-generated format.
        for (DateTimeFormatter formatter : ISO) {
            Optional<LocalDate> parsed = tryParse(value, formatter);
            if (parsed.isPresent()) {
                return parsed;
            }
        }

        String expanded = expandTwoDigitYear(value);
        List<DateTimeFormatter> numeric = order == DateOrder.MONTH_FIRST ? MONTH_FIRST : DAY_FIRST;
        for (DateTimeFormatter formatter : numeric) {
            Optional<LocalDate> parsed = tryParse(expanded, formatter);
            if (parsed.isPresent()) {
                return parsed;
            }
        }

        for (DateTimeFormatter formatter : TEXTUAL) {
            Optional<LocalDate> parsed = tryParse(value, formatter);
            if (parsed.isPresent()) {
                return parsed;
            }
        }

        // Last resort: try the other order, so a mis-detected column does not lose every row.
        List<DateTimeFormatter> alternate = order == DateOrder.MONTH_FIRST ? DAY_FIRST : MONTH_FIRST;
        for (DateTimeFormatter formatter : alternate) {
            Optional<LocalDate> parsed = tryParse(expanded, formatter);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    public static Optional<LocalDate> parseAny(String raw) {
        return parse(raw, DateOrder.DAY_FIRST);
    }

    private static Optional<LocalDate> tryParse(String value, DateTimeFormatter formatter) {
        try {
            return Optional.of(LocalDate.parse(value, formatter));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private static String expandTwoDigitYear(String value) {
        Matcher matcher = TWO_DIGIT_YEAR.matcher(value);
        if (!matcher.matches()) {
            return value;
        }
        int yy = Integer.parseInt(matcher.group(3));
        int yyyy = yy < 70 ? 2000 + yy : 1900 + yy;
        return matcher.group(1) + "/" + matcher.group(2) + "/" + yyyy;
    }

    public static Optional<LocalDate> fromExcelSerial(BigDecimal serial) {
        if (serial == null) {
            return Optional.empty();
        }
        double value = serial.doubleValue();
        if (value < 1 || value > 80000) {
            return Optional.empty();
        }
        long days = (long) Math.floor(value);
        return Optional.of(EXCEL_EPOCH.plusDays(days));
    }

    /**
     * Votes on the column's date order using values where one component is greater than 12 and
     * therefore cannot be a month. Ties default to day-first, which is the Indian convention.
     */
    public static DateOrder detectOrder(List<String> samples) {
        int dayFirstEvidence = 0;
        int monthFirstEvidence = 0;
        for (String sample : samples) {
            if (sample == null) {
                continue;
            }
            String value = TextNormalizer.clean(sample);
            Matcher matcher = AMBIGUOUS_NUMERIC.matcher(value);
            if (!matcher.matches()) {
                continue;
            }
            int first = Integer.parseInt(matcher.group(1));
            int second = Integer.parseInt(matcher.group(2));
            if (first > 12 && second <= 12) {
                dayFirstEvidence++;
            } else if (second > 12 && first <= 12) {
                monthFirstEvidence++;
            }
        }
        if (monthFirstEvidence > dayFirstEvidence) {
            return DateOrder.MONTH_FIRST;
        }
        return DateOrder.DAY_FIRST;
    }

    public static boolean isAmbiguousNumeric(String raw) {
        return raw != null && AMBIGUOUS_NUMERIC.matcher(TextNormalizer.clean(raw)).matches();
    }
}
