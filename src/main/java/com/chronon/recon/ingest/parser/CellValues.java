package com.chronon.recon.ingest.parser;

import com.chronon.recon.domain.Money;
import com.chronon.recon.ingest.RawCell;
import com.chronon.recon.ingest.normalizer.DateOrder;
import com.chronon.recon.ingest.normalizer.DateParser;
import com.chronon.recon.ingest.normalizer.MoneyParser;

import java.time.LocalDate;
import java.util.Optional;

/** Converts typed spreadsheet cells into domain values, preferring the typed form over the text. */
public final class CellValues {

    private CellValues() {
    }

    public static Optional<Money> money(RawCell cell) {
        if (cell == null || cell.isBlank()) {
            return Optional.empty();
        }
        if (cell.number() != null) {
            return Optional.of(Money.of(cell.number()));
        }
        return MoneyParser.parse(cell.text());
    }

    public static Optional<LocalDate> date(RawCell cell, DateOrder order) {
        if (cell == null || cell.isBlank()) {
            return Optional.empty();
        }
        if (cell.date() != null) {
            return Optional.of(cell.date());
        }
        if (cell.number() != null) {
            Optional<LocalDate> serial = DateParser.fromExcelSerial(cell.number());
            if (serial.isPresent()) {
                return serial;
            }
        }
        return DateParser.parse(cell.text(), order);
    }
}
