package com.chronon.recon.ingest.parser;

import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.Money;
import com.chronon.recon.ingest.RawCell;
import com.chronon.recon.ingest.RawRow;
import com.chronon.recon.ingest.RawTable;
import com.chronon.recon.ingest.mapping.ColumnMapping;
import com.chronon.recon.ingest.mapping.Fields;
import com.chronon.recon.ingest.mapping.MappingProfile;
import com.chronon.recon.ingest.normalizer.DateOrder;
import com.chronon.recon.ingest.normalizer.DateParser;
import com.chronon.recon.ingest.normalizer.ReferenceExtractor;
import com.chronon.recon.ingest.normalizer.TextNormalizer;
import com.chronon.recon.domain.quality.DataQualityLog;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads a bank statement into {@link CanonicalBankTxn} records.
 *
 * <p>Handles both layouts seen in practice: separate Debit/Credit columns, and a single Amount
 * column with a Dr/Cr indicator. Rows that carry no money (opening/closing balance, statement
 * summaries) are skipped with an audit note rather than being reported as unmatched debits.</p>
 */
@Component
public class BankStatementParser {

    private static final String SOURCE = "Bank statement";

    public List<CanonicalBankTxn> parse(RawTable table, ColumnMapping mapping,
                                        MappingProfile profile, DataQualityLog quality) {
        DateOrder order = detectDateOrder(table, mapping);
        List<CanonicalBankTxn> transactions = new ArrayList<>();

        for (RawRow row : table.rows()) {
            Optional<LocalDate> txnDate = CellValues.date(row.cell(mapping.index(Fields.TXN_DATE)), order);
            Optional<LocalDate> valueDate = CellValues.date(row.cell(mapping.index(Fields.VALUE_DATE)), order);
            String rowLabel = firstText(row);

            // A "Total" or "Closing Balance" row carries amounts but no date. Without this guard it
            // would be parsed as a real movement and double every total in the statement.
            if (txnDate.isEmpty() && valueDate.isEmpty() && matchesSkipPattern(rowLabel, profile)) {
                quality.info(SOURCE, row.rowNumber(), null, "Skipped summary row: '" + rowLabel + "'.");
                continue;
            }

            Optional<Money> debit = CellValues.money(row.cell(mapping.index(Fields.DEBIT)));
            Optional<Money> credit = CellValues.money(row.cell(mapping.index(Fields.CREDIT)));

            if (mapping.has(Fields.AMOUNT) && mapping.has(Fields.DR_CR) && debit.isEmpty() && credit.isEmpty()) {
                Optional<Money> combined = CellValues.money(row.cell(mapping.index(Fields.AMOUNT)));
                if (combined.isPresent()) {
                    String indicator = TextNormalizer.clean(row.text(mapping.index(Fields.DR_CR)));
                    boolean isDebit = looksLikeDebit(indicator, combined.get());
                    if (indicator.isBlank()) {
                        quality.warn(SOURCE, row.rowNumber(), Fields.DR_CR,
                                "Missing Dr/Cr indicator; direction inferred from the amount sign.");
                    }
                    if (isDebit) {
                        debit = Optional.of(combined.get().abs());
                    } else {
                        credit = Optional.of(combined.get().abs());
                    }
                }
            }

            if (debit.isPresent() && credit.isPresent()
                    && !debit.get().isZero() && !credit.get().isZero()) {
                quality.warn(SOURCE, row.rowNumber(), null,
                        "Row has both a debit (" + debit.get().display() + ") and a credit ("
                                + credit.get().display() + "); netted to a single movement.");
                Money net = credit.get().minus(debit.get());
                if (net.isPositive()) {
                    credit = Optional.of(net);
                    debit = Optional.empty();
                } else if (net.isNegative()) {
                    debit = Optional.of(net.abs());
                    credit = Optional.empty();
                } else {
                    debit = Optional.empty();
                    credit = Optional.empty();
                }
            }

            if (debit.isEmpty() && credit.isEmpty()) {
                Optional<Money> balance = CellValues.money(row.cell(mapping.index(Fields.BALANCE)));
                if (balance.isPresent() || matchesSkipPattern(rowLabel, profile)) {
                    quality.info(SOURCE, row.rowNumber(), null,
                            "Skipped non-transaction row: '" + rowLabel + "'.");
                } else {
                    quality.warn(SOURCE, row.rowNumber(), null,
                            "Row has no debit or credit amount and was skipped: '" + rowLabel + "'.");
                }
                continue;
            }

            if (txnDate.isEmpty() && valueDate.isEmpty()) {
                quality.error(SOURCE, row.rowNumber(), Fields.TXN_DATE,
                        "Transaction has no readable date and was not reconciled: '" + rowLabel + "'.");
                continue;
            }
            if (txnDate.isEmpty()) {
                quality.warn(SOURCE, row.rowNumber(), Fields.TXN_DATE,
                        "Transaction date missing; using the value date " + valueDate.orElse(null) + ".");
            }

            String narration = TextNormalizer.clean(row.text(mapping.index(Fields.NARRATION)));
            String chequeRef = TextNormalizer.clean(row.text(mapping.index(Fields.CHEQUE_REF)));
            if (narration.isBlank() && chequeRef.isBlank()) {
                quality.warn(SOURCE, row.rowNumber(), Fields.NARRATION,
                        "Transaction has no narration or reference; matching can only use amount and date.");
            }

            transactions.add(new CanonicalBankTxn(
                    row.rowNumber(),
                    txnDate.orElse(null),
                    valueDate.orElse(null),
                    narration,
                    chequeRef,
                    debit.orElse(null),
                    credit.orElse(null),
                    CellValues.money(row.cell(mapping.index(Fields.BALANCE))).orElse(null),
                    ReferenceExtractor.tokens(narration, chequeRef)
            ));
        }
        return transactions;
    }

    private boolean looksLikeDebit(String indicator, Money amount) {
        String value = indicator == null ? "" : indicator.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("d") || value.startsWith("w") || value.startsWith("p")) {
            return true;
        }
        if (value.startsWith("c")) {
            return false;
        }
        return amount.isNegative();
    }

    private DateOrder detectDateOrder(RawTable table, ColumnMapping mapping) {
        List<String> samples = new ArrayList<>();
        for (RawRow row : table.rows()) {
            for (String field : List.of(Fields.TXN_DATE, Fields.VALUE_DATE)) {
                RawCell cell = row.cell(mapping.index(field));
                if (cell.date() == null && !cell.text().isBlank()) {
                    samples.add(cell.text());
                }
            }
        }
        return DateParser.detectOrder(samples);
    }

    private boolean matchesSkipPattern(String label, MappingProfile profile) {
        if (label == null || label.isBlank()) {
            return false;
        }
        for (String pattern : profile.skipRowPatterns()) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(label).find()) {
                return true;
            }
        }
        return false;
    }

    private String firstText(RawRow row) {
        for (RawCell cell : row.cells()) {
            String text = TextNormalizer.clean(cell.asText());
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }
}
