package com.chronon.recon.ingest.normalizer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls reference-like tokens out of free-text narrations ("NEFT CR-HDFC0001234-RAHUL-PO-1042").
 *
 * <p>Rather than searching every payout id against every narration (O(n*m)), tokens are extracted
 * once per bank line and indexed, then looked up by payout reference.</p>
 */
public final class ReferenceExtractor {

    private static final Pattern REF_PATTERN = Pattern.compile("(?i)\\b([a-z]{2,})[-_]?([0-9]+[a-z]*)\\b");

    private ReferenceExtractor() {
    }

    public static List<String> tokens(String... texts) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            Matcher matcher = REF_PATTERN.matcher(text);
            while (matcher.find()) {
                tokens.add((matcher.group(1) + matcher.group(2)).toLowerCase(Locale.ROOT));
                tokens.add(matcher.group(2).toLowerCase(Locale.ROOT));
            }
            for (String part : text.split("[^A-Za-z0-9]+")) {
                String token = part.trim().toLowerCase(Locale.ROOT);
                if (token.length() < 4) {
                    continue;
                }
                boolean hasDigit = token.chars().anyMatch(Character::isDigit);
                if (hasDigit) {
                    tokens.add(token);
                    String digits = token.replaceAll("\\D", "");
                    if (digits.length() >= 4) {
                        tokens.add(digits);
                        if (digits.length() > 6) {
                            tokens.add(digits.substring(digits.length() - 6));
                        }
                    }
                } else if (token.length() >= 6) {
                    tokens.add(token);
                }
            }
        }
        return List.copyOf(tokens);
    }

    public static String foldRef(String reference) {
        return reference == null ? "" : reference.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static String digits(String reference) {
        return reference == null ? "" : reference.replaceAll("\\D", "");
    }

    /**
     * Reference match with a tolerance for truncation: banks frequently keep only the last six
     * digits of a reference.
     */
    public static boolean matches(String reference, List<String> tokens) {
        String folded = foldRef(reference);
        if (folded.isEmpty() || tokens.isEmpty()) {
            return false;
        }
        if (tokens.contains(folded)) {
            return true;
        }
        String digits = digits(reference);
        if (digits.length() >= 4 && tokens.contains(digits)) {
            return true;
        }
        if (digits.length() > 6) {
            String suffix = digits.substring(digits.length() - 6);
            for (String token : tokens) {
                if (token.endsWith(suffix)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<String> lookupKeys(String reference) {
        List<String> keys = new ArrayList<>();
        String folded = foldRef(reference);
        if (!folded.isEmpty()) {
            keys.add(folded);
        }
        String digits = digits(reference);
        if (digits.length() >= 4 && !digits.equals(folded)) {
            keys.add(digits);
        }
        if (digits.length() > 6) {
            keys.add(digits.substring(digits.length() - 6));
        }
        return keys;
    }
}
