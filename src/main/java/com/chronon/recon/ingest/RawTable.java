package com.chronon.recon.ingest;

import java.util.List;

/**
 * A sheet that has been located, header-detected and normalised into rows of typed cells.
 *
 * @param headerRowNumber 1-based row number of the detected header
 */
public record RawTable(
        String sheetName,
        int headerRowNumber,
        List<String> headers,
        List<RawRow> rows
) {

    public RawTable {
        headers = headers == null ? List.of() : List.copyOf(headers);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public int rowCount() {
        return rows.size();
    }

    public String header(int index) {
        return index >= 0 && index < headers.size() ? headers.get(index) : "";
    }
}
