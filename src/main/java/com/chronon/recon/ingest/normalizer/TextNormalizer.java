package com.chronon.recon.ingest.normalizer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Text hygiene for messy spreadsheet data: non-breaking spaces, zero-width characters, stray
 * control characters and inconsistent casing are the usual culprits when an exact string match
 * "should" work but does not.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /** Cleans a value for display and for storage. Preserves meaningful words and digits. */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replace('\u2007', ' ')
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]", "");
        return value.replaceAll("\\s+", " ").trim();
    }

    /** Aggressive folding for comparisons only: lower case, no accents, alphanumerics only. */
    public static String fold(String raw) {
        String cleaned = clean(raw);
        String decomposed = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Folding that keeps word boundaries, for containment checks on column names. */
    public static String foldWords(String raw) {
        String cleaned = clean(raw);
        String decomposed = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    public static List<String> words(String raw) {
        String folded = foldWords(raw);
        if (folded.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String word : folded.split(" ")) {
            if (!word.isBlank()) {
                out.add(word);
            }
        }
        return out;
    }

    public static boolean containsFolded(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isBlank()) {
            return false;
        }
        return fold(haystack).contains(fold(needle));
    }
}
