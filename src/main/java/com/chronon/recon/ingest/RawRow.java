package com.chronon.recon.ingest;

import java.util.List;

/** A data row with its 1-based spreadsheet row number, so every finding can be traced back. */
public record RawRow(int rowNumber, List<RawCell> cells) {

    public RawRow {
        cells = cells == null ? List.of() : List.copyOf(cells);
    }

    public RawCell cell(Integer index) {
        if (index == null || index < 0 || index >= cells.size()) {
            return RawCell.blank();
        }
        return cells.get(index);
    }

    public String text(Integer index) {
        return cell(index).asText();
    }

    public boolean isBlank() {
        return cells.stream().allMatch(RawCell::isBlank);
    }
}
