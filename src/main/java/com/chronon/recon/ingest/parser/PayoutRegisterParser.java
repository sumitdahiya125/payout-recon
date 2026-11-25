package com.chronon.recon.ingest.parser;

import com.chronon.recon.domain.CanonicalPayout;
import com.chronon.recon.domain.Money;
import com.chronon.recon.domain.PayoutStatus;
import com.chronon.recon.ingest.RawCell;
import com.chronon.recon.ingest.RawRow;
import com.chronon.recon.ingest.RawTable;
import com.chronon.recon.ingest.mapping.ColumnMapping;
import com.chronon.recon.ingest.mapping.Fields;
import com.chronon.recon.ingest.mapping.MappingProfile;
import com.chronon.recon.ingest.normalizer.DateOrder;
import com.chronon.recon.ingest.normalizer.DateParser;
import com.chronon.recon.ingest.normalizer.TextNormalizer;
import com.chronon.recon.domain.quality.DataQualityLog;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the payout register into {@link CanonicalPayout} records.
 *
 * <p>Rows that cannot be reconciled at all (no id, no amount, no date) are reported as errors and
 * skipped; everything questionable but usable is kept and flagged, because dropping money is worse
 * than reporting it.</p>
 */
@Component
public class PayoutRegisterParser {

    private static final String SOURCE = "Payout register";

    public List<CanonicalPayout> parse(RawTable table, ColumnMapping mapping,
                                       MappingProfile profile, DataQualityLog quality) {
        DateOrder order = detectDateOrder(table, mapping);
        List<CanonicalPayout> payouts = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (RawRow row : table.rows()) {
            if (isSkippedRow(row, mapping, profile)) {
                quality.info(SOURCE, row.rowNumber(), null,
                        "Skipped total/summary row: '" + firstText(row) + "'.");
                continue;
            }

            String payoutId = TextNormalizer.clean(row.text(mapping.index(Fields.PAYOUT_ID)));
            if (payoutId.isEmpty()) {
                quality.error(SOURCE, row.rowNumber(), Fields.PAYOUT_ID,
                        "Row has no payout id and was not reconciled.");
                continue;
            }

            Optional<Money> amount = CellValues.money(row.cell(mapping.index(Fields.AMOUNT)));
            if (amount.isEmpty()) {
                quality.error(SOURCE, row.rowNumber(), Fields.AMOUNT,
                        "Payout " + payoutId + " has an unreadable amount ('"
                                + row.text(mapping.index(Fields.AMOUNT)) + "') and was not reconciled.");
                continue;
            }
            if (!amount.get().isPositive()) {
                quality.warn(SOURCE, row.rowNumber(), Fields.AMOUNT,
                        "Payout " + payoutId + " has a non-positive amount (" + amount.get().display() + ").");
            }

            Optional<LocalDate> payoutDate = CellValues.date(row.cell(mapping.index(Fields.PAYOUT_DATE)), order);
            if (payoutDate.isEmpty()) {
                quality.error(SOURCE, row.rowNumber(), Fields.PAYOUT_DATE,
                        "Payout " + payoutId + " has an unreadable payout date ('"
                                + row.text(mapping.index(Fields.PAYOUT_DATE)) + "') and was not reconciled.");
                continue;
            }

            String statusText = row.text(mapping.index(Fields.STATUS));
            PayoutStatus status = PayoutStatus.from(statusText);
            if (!statusText.isBlank() && status == PayoutStatus.UNKNOWN) {
                quality.warn(SOURCE, row.rowNumber(), Fields.STATUS,
                        "Unrecognised status '" + statusText + "' for payout " + payoutId
                                + "; treated as payable.");
            }

            if (!seenIds.add(payoutId)) {
                quality.warn(SOURCE, row.rowNumber(), Fields.PAYOUT_ID,
                        "Duplicate payout id '" + payoutId + "' in the register. Both rows are reconciled "
                                + "separately so the duplicate is visible in the report.");
            }

            List<String> claimIds = splitIds(row.text(mapping.index(Fields.CLAIM_IDS)));

            payouts.add(new CanonicalPayout(
                    payoutId,
                    TextNormalizer.clean(row.text(mapping.index(Fields.EMPLOYEE_ID))),
                    TextNormalizer.clean(row.text(mapping.index(Fields.EMPLOYEE_NAME))),
                    TextNormalizer.clean(row.text(mapping.index(Fields.BANK_ACCOUNT))),
                    null,
                    TextNormalizer.clean(row.text(mapping.index(Fields.IFSC))),
                    amount.get(),
                    payoutDate.get(),
                    claimIds,
                    status,
                    row.rowNumber()
            ));
        }
        return payouts;
    }

    private DateOrder detectDateOrder(RawTable table, ColumnMapping mapping) {
        Integer column = mapping.index(Fields.PAYOUT_DATE);
        if (column == null) {
            return DateOrder.DAY_FIRST;
        }
        List<String> samples = new ArrayList<>();
        for (RawRow row : table.rows()) {
            RawCell cell = row.cell(column);
            if (cell.date() == null && !cell.text().isBlank()) {
                samples.add(cell.text());
            }
        }
        return DateParser.detectOrder(samples);
    }

    private boolean isSkippedRow(RawRow row, ColumnMapping mapping, MappingProfile profile) {
        String id = row.text(mapping.index(Fields.PAYOUT_ID));
        String label = id == null || id.isBlank() ? firstText(row) : id;
        if (label.isBlank()) {
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

    private List<String> splitIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String part : raw.split("[,;/|\\s]+")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                ids.add(value);
            }
        }
        return ids;
    }

    static String normaliseStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
