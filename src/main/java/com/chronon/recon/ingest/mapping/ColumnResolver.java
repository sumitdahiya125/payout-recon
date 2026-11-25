package com.chronon.recon.ingest.mapping;

import com.chronon.recon.ingest.normalizer.TextNormalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Matches a profile's field aliases against real header text.
 *
 * <p>Scoring is deliberately transparent: exact alias match, then containment, then edit-distance
 * similarity. Mappings below 80% are reported as warnings so a human can confirm them.</p>
 */
@Component
public class ColumnResolver {

    private static final double MIN_ACCEPTABLE = 0.62;
    private static final double WARN_BELOW = 0.80;

    public record HeaderCandidate(int index, ColumnMapping mapping, double rank) {
    }

    /** Resolves every field against one header row, without considering other rows. */
    public ColumnMapping resolve(List<String> headers, MappingProfile profile, int headerRowIndex, int headerRowNumber) {
        record Pair(String field, int column, double score) {
        }

        List<Pair> pairs = new ArrayList<>();
        for (Map.Entry<String, FieldSpec> entry : profile.fields().entrySet()) {
            String field = entry.getKey();
            for (int column = 0; column < headers.size(); column++) {
                double score = scoreHeader(headers.get(column), entry.getValue());
                if (score >= MIN_ACCEPTABLE) {
                    pairs.add(new Pair(field, column, score));
                }
            }
        }

        pairs.sort(Comparator.comparingDouble(Pair::score).reversed()
                .thenComparing(Pair::field)
                .thenComparingInt(Pair::column));

        Map<String, Integer> columns = new LinkedHashMap<>();
        Map<String, Double> scores = new LinkedHashMap<>();
        Set<Integer> usedColumns = new java.util.HashSet<>();
        for (Pair pair : pairs) {
            if (columns.containsKey(pair.field()) || usedColumns.contains(pair.column())) {
                continue;
            }
            columns.put(pair.field(), pair.column());
            scores.put(pair.field(), pair.score());
            usedColumns.add(pair.column());
        }

        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (String required : profile.requiredKeys()) {
            if (!columns.containsKey(required)) {
                missing.add(required);
            }
        }
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            if (entry.getValue() < WARN_BELOW) {
                warnings.add("Field '" + entry.getKey() + "' was mapped to column '"
                        + safe(headers, columns.get(entry.getKey())) + "' with only "
                        + Math.round(entry.getValue() * 100) + "% confidence - please verify the mapping.");
            }
        }
        return new ColumnMapping(headerRowIndex, headerRowNumber, headers, columns, scores, missing, warnings);
    }

    /**
     * Picks the row that looks most like a header. Bank exports routinely have a preamble of five
     * to ten rows before the real header, so this cannot be hard-coded to row 1.
     */
    public HeaderCandidate bestHeader(List<List<String>> candidateRows, MappingProfile profile) {
        Set<String> required = profile.requiredKeys();
        HeaderCandidate best = null;
        for (int index = 0; index < candidateRows.size(); index++) {
            List<String> row = candidateRows.get(index);
            ColumnMapping mapping = resolve(row, profile, index, index + 1);
            int resolvedRequired = required.size() - mapping.missingRequired().size();
            if (resolvedRequired == 0) {
                continue;
            }
            double rank = resolvedRequired * 100.0
                    + mapping.scores().values().stream().mapToDouble(Double::doubleValue).sum();
            if (best == null || rank > best.rank()) {
                best = new HeaderCandidate(index, mapping, rank);
            }
        }
        if (best != null) {
            return best;
        }
        // Nothing usable: return the row with the most non-blank cells so the error message is useful.
        int bestIndex = 0;
        int bestCells = -1;
        for (int index = 0; index < candidateRows.size(); index++) {
            int cells = (int) candidateRows.get(index).stream().filter(c -> !c.isBlank()).count();
            if (cells > bestCells) {
                bestCells = cells;
                bestIndex = index;
            }
        }
        List<String> headers = candidateRows.isEmpty() ? List.of() : candidateRows.get(bestIndex);
        return new HeaderCandidate(bestIndex, resolve(headers, profile, bestIndex, bestIndex + 1), -1);
    }

    private String safe(List<String> headers, Integer index) {
        if (index == null || index < 0 || index >= headers.size()) {
            return "";
        }
        return headers.get(index);
    }

    double scoreHeader(String header, FieldSpec spec) {
        String folded = TextNormalizer.fold(header);
        if (folded.isEmpty()) {
            return 0;
        }
        double best = 0;
        for (String alias : spec.aliases()) {
            String foldedAlias = TextNormalizer.fold(alias);
            if (foldedAlias.isEmpty()) {
                continue;
            }
            if (folded.equals(foldedAlias)) {
                return 1.0;
            }
            if (folded.contains(foldedAlias) || foldedAlias.contains(folded)) {
                best = Math.max(best, 0.88);
            }
            best = Math.max(best, 0.85 * similarity(folded, foldedAlias));
        }
        return best;
    }

    /** Blend of edit-distance similarity and word overlap; tolerant of "Txn Date" vs "Transaction Date". */
    private double similarity(String left, String right) {
        int maxLength = Math.max(left.length(), right.length());
        double edit = maxLength == 0 ? 1 : 1.0 - ((double) levenshtein(left, right) / maxLength);
        double jaccard = jaccard(TextNormalizer.words(left), TextNormalizer.words(right));
        return Math.max(edit, jaccard);
    }

    private double jaccard(List<String> left, List<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        Set<String> a = new java.util.HashSet<>(left);
        Set<String> b = new java.util.HashSet<>(right);
        long intersection = a.stream().filter(b::contains).count();
        long union = a.size() + (long) b.size() - intersection;
        return union == 0 ? 0 : (double) intersection / union;
    }

    static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    public String normalise(String value) {
        return TextNormalizer.fold(value).toLowerCase(Locale.ROOT);
    }
}
