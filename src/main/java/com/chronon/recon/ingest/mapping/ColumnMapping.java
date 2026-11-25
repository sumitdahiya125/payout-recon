package com.chronon.recon.ingest.mapping;

import java.util.List;
import java.util.Map;

/**
 * The result of resolving a profile against a concrete header row.
 *
 * @param headerRowIndex 0-based index of the header row inside the scanned window
 * @param headerRowNumber 1-based spreadsheet row number, for messages
 */
public record ColumnMapping(
        int headerRowIndex,
        int headerRowNumber,
        List<String> headers,
        Map<String, Integer> columns,
        Map<String, Double> scores,
        List<String> missingRequired,
        List<String> warnings
) {

    public ColumnMapping {
        headers = headers == null ? List.of() : List.copyOf(headers);
        columns = columns == null ? Map.of() : Map.copyOf(columns);
        scores = scores == null ? Map.of() : Map.copyOf(scores);
        missingRequired = missingRequired == null ? List.of() : List.copyOf(missingRequired);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public Integer index(String field) {
        return columns.get(field);
    }

    public boolean has(String field) {
        return columns.containsKey(field);
    }

    public boolean usable() {
        return missingRequired.isEmpty();
    }

    public double score(String field) {
        return scores.getOrDefault(field, 0d);
    }

    public int requiredResolved(int requiredCount) {
        return requiredCount - missingRequired.size();
    }

    public String headerAt(int index) {
        return index >= 0 && index < headers.size() ? headers.get(index) : "";
    }

    public String describe() {
        StringBuilder text = new StringBuilder();
        columns.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> text.append(entry.getKey())
                        .append(" -> '")
                        .append(headerAt(entry.getValue()))
                        .append("' (")
                        .append(Math.round(score(entry.getKey()) * 100))
                        .append("%) ")
                );
        return text.toString().trim();
    }
}
