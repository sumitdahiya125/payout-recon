package com.chronon.recon.report;

import com.chronon.recon.domain.BankFinding;
import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.CategoryTotal;
import com.chronon.recon.domain.MatchCategory;
import com.chronon.recon.domain.Money;
import com.chronon.recon.domain.PayoutMatch;
import com.chronon.recon.domain.Summary;
import com.chronon.recon.ingest.mapping.ColumnMapping;
import com.chronon.recon.domain.quality.Issue;
import com.chronon.recon.service.ReconciliationResult;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Writes the finance-facing workbook.
 *
 * <p>Uses SXSSF (streaming) rather than XSSF so a large reconciliation does not need the whole
 * workbook in memory. Every sheet is designed to be filtered and checked by a non-engineer: the
 * summary shows the tie-out, and each exception row carries the reason in words.</p>
 */
@Component
public class ReportWriter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

    public byte[] write(ReconciliationResult result) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        workbook.setCompressTempFiles(true);
        try {
            Styles styles = new Styles(workbook);
            writeSummary(workbook, styles, result);
            writePayoutSheet(workbook, styles, "Payout Reconciliation", result.outcome().payoutMatches(), true);
            writeBankFindings(workbook, styles, "Bank Statement Activity", result.outcome().bankFindings());
            writeQualityLog(workbook, styles, result);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    // ------------------------------------------------------------------ summary

    private void writeSummary(SXSSFWorkbook workbook, Styles styles, ReconciliationResult result) {
        Sheet sheet = workbook.createSheet("Summary & Tie-Out");
        sheet.setColumnWidth(0, 46 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 18 * 256);
        sheet.setColumnWidth(3, 70 * 256);

        Summary summary = result.summary();
        int rowIndex = 0;

        Row title = sheet.createRow(rowIndex++);
        cell(title, 0, "Payout Reconciliation Report", styles.title);
        rowIndex++;

        rowIndex = meta(sheet, rowIndex, "Run id", result.runId());
        rowIndex = meta(sheet, rowIndex, "Generated",
                TIMESTAMP.format(result.generatedAt().atZone(ZoneId.systemDefault())));
        rowIndex = meta(sheet, rowIndex, "Payout register", result.payoutFileName()
                + "  (profile: " + result.payoutProfile().id() + ")");
        rowIndex = meta(sheet, rowIndex, "Bank statement", result.bankFileName()
                + "  (profile: " + result.bankProfile().id() + ")");
        rowIndex = meta(sheet, rowIndex, "Matching policy", result.matchingPolicy());
        rowIndex++;

        rowIndex = section(sheet, rowIndex, "Payout register totals", styles);
        rowIndex = tableHeader(sheet, rowIndex, styles, "Metric", "Count", "Amount");
        rowIndex = tableRow(sheet, rowIndex, styles, "Payouts in register", summary.payoutCount(),
                summary.registerTotal());
        rowIndex = tableRow(sheet, rowIndex, styles, "Excluded by status (failed/cancelled)",
                summary.excludedPayoutCount(), summary.excludedPayoutTotal());
        rowIndex = tableRow(sheet, rowIndex, styles, "Payable payouts",
                summary.payoutCount() - summary.excludedPayoutCount(),
                summary.registerTotal().minus(summary.excludedPayoutTotal()));
        rowIndex++;

        rowIndex = section(sheet, rowIndex, "Reconciliation outcome", styles);
        rowIndex = tableHeader(sheet, rowIndex, styles, "Category", "Count", "Amount");
        for (CategoryTotal total : summary.payoutTotals()) {
            rowIndex = tableRow(sheet, rowIndex, styles, total.category().label(), total.count(), total.amount());
        }
        rowIndex = tableRow(sheet, rowIndex, styles, "Total accounted for", summary.payoutCount(),
                summary.registerTotal());
        rowIndex = residualRow(sheet, rowIndex, styles, "Difference (must be 0.00)", summary.payoutResidual());
        rowIndex++;

        rowIndex = section(sheet, rowIndex, "Bank statement totals", styles);
        rowIndex = tableHeader(sheet, rowIndex, styles, "Metric", "Count", "Amount");
        rowIndex = tableRow(sheet, rowIndex, styles, "Bank lines read", summary.bankLineCount(), null);
        rowIndex = tableRow(sheet, rowIndex, styles, "Total credits", null, summary.bankCreditTotal());
        rowIndex = tableRow(sheet, rowIndex, styles, "Total debits", null, summary.bankDebitTotal());
        rowIndex = tableRow(sheet, rowIndex, styles, "Net movement", null, summary.bankNetTotal());
        rowIndex++;

        rowIndex = section(sheet, rowIndex, "Bank line disposition", styles);
        rowIndex = tableHeader(sheet, rowIndex, styles, "Category", "Lines", "Amount");
        for (CategoryTotal total : summary.bankTotals()) {
            rowIndex = tableRow(sheet, rowIndex, styles, total.category().label(), total.count(), total.amount());
        }
        rowIndex = tableRow(sheet, rowIndex, styles, "Total accounted for", summary.bankLineCount(),
                summary.bankNetTotal());
        rowIndex = residualRow(sheet, rowIndex, styles, "Difference (must be 0.00)", summary.bankResidual());
        rowIndex++;

        rowIndex = section(sheet, rowIndex, "Tie-out check", styles);
        Row statusRow = sheet.createRow(rowIndex++);
        cell(statusRow, 0, summary.tieOutOk() ? "PASSED" : "FAILED",
                summary.tieOutOk() ? styles.pass : styles.fail);
        String tieOutMessage;
        if (summary.tieOutOk()) {
            tieOutMessage = "Payout and bank partitions both reconcile exactly to the input files with zero errors.";
        } else if (!summary.payoutResidual().isZero() || !summary.bankResidual().isZero()) {
            tieOutMessage = "The totals do not reconcile (residual mismatch). Do not act on this report until the difference is explained.";
        } else {
            tieOutMessage = "Totals balance, but " + summary.errorCount()
                    + " fatal data quality error(s) occurred. Review the Data quality log.";
        }
        cell(statusRow, 1, tieOutMessage, styles.wrap);
        rowIndex++;

        rowIndex = section(sheet, rowIndex, "Match quality", styles);
        rowIndex = meta(sheet, rowIndex, "Automatically matched",
                summary.matchedCount() + " of " + summary.payoutCount() + " payouts ("
                        + String.format("%.1f", summary.autoMatchRate()) + "%)");
        rowIndex = meta(sheet, rowIndex, "Needs review", String.valueOf(summary.reviewCount()));
        rowIndex = meta(sheet, rowIndex, "Unmatched payouts", String.valueOf(summary.unmatchedPayoutCount()));
        rowIndex = meta(sheet, rowIndex, "Matched payout total vs matched bank total",
                summary.matchedTotal().display() + " vs " + summary.matchedBankTotal().display());
        rowIndex++;

        rowIndex = section(sheet, rowIndex, "Review reasons", styles);
        rowIndex = tableHeader(sheet, rowIndex, styles, "Reason", "Payouts", null);
        if (summary.reviewReasons().isEmpty()) {
            rowIndex = meta(sheet, rowIndex, "None", "-");
        } else {
            for (Map.Entry<String, Integer> entry : summary.reviewReasons().entrySet()) {
                rowIndex = tableRow(sheet, rowIndex, styles, entry.getKey(), entry.getValue(), null);
            }
        }
        rowIndex++;

        rowIndex = section(sheet, rowIndex, "Data quality", styles);
        rowIndex = meta(sheet, rowIndex, "Errors (rows not reconciled)", String.valueOf(summary.errorCount()));
        rowIndex = meta(sheet, rowIndex, "Warnings", String.valueOf(summary.warningCount()));
        rowIndex = meta(sheet, rowIndex, "All observations", String.valueOf(summary.issueCount()));
        rowIndex++;

        rowIndex = section(sheet, rowIndex, "Column mapping used", styles);
        rowIndex = tableHeader4(sheet, rowIndex, styles, "Field", "Required", "Match Score", "Detected File Header");
        rowIndex = mappingRows(sheet, rowIndex, styles, result.payoutProfile(), result.payoutMapping());
        rowIndex = mappingRows(sheet, rowIndex, styles, result.bankProfile(), result.bankMapping());

        sheet.createFreezePane(0, 2);
    }

    private int meta(Sheet sheet, int rowIndex, String label, String value) {
        Row row = sheet.createRow(rowIndex);
        cell(row, 0, label, null);
        cell(row, 1, value, null);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 1, 3));
        return rowIndex + 1;
    }

    private int section(Sheet sheet, int rowIndex, String title, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        cell(row, 0, title, styles.section);
        return rowIndex + 1;
    }

    private int tableHeader(Sheet sheet, int rowIndex, Styles styles, String first, String second, String third) {
        Row row = sheet.createRow(rowIndex);
        cell(row, 0, first, styles.header);
        cell(row, 1, second, styles.header);
        cell(row, 2, third == null ? "" : third, styles.header);
        return rowIndex + 1;
    }

    private int tableHeader4(Sheet sheet, int rowIndex, Styles styles, String first, String second, String third, String fourth) {
        Row row = sheet.createRow(rowIndex);
        cell(row, 0, first, styles.header);
        cell(row, 1, second, styles.header);
        cell(row, 2, third, styles.header);
        cell(row, 3, fourth, styles.header);
        return rowIndex + 1;
    }

    private int tableRow(Sheet sheet, int rowIndex, Styles styles, String label, Integer count, Money amount) {
        Row row = sheet.createRow(rowIndex);
        cell(row, 0, label, styles.text);
        if (count != null) {
            cell(row, 1, count, styles.integer);
        } else {
            cell(row, 1, "", styles.text);
        }
        if (amount != null) {
            money(row, 2, amount, styles.money);
        } else {
            cell(row, 2, "", styles.text);
        }
        return rowIndex + 1;
    }

    private int residualRow(Sheet sheet, int rowIndex, Styles styles, String label, Money residual) {
        Row row = sheet.createRow(rowIndex);
        CellStyle style = residual.isZero() ? styles.pass : styles.fail;
        cell(row, 0, label, style);
        cell(row, 1, "", style);
        money(row, 2, residual, style);
        return rowIndex + 1;
    }

    // ------------------------------------------------------------------ payout sheets

    private void writePayoutSheet(SXSSFWorkbook workbook, Styles styles, String name,
                                  List<PayoutMatch> matches, boolean detailed) {
        Sheet sheet = workbook.createSheet(name);
        String[] headers = detailed
                ? new String[]{"Payout ID", "Employee", "Account", "IFSC", "Payout date", "Amount", "Status",
                "Category", "Confidence", "Rule", "Bank date", "Bank narration", "Bank amount", "Difference",
                "Explanation", "Source rows"}
                : new String[]{"Payout ID", "Employee", "Payout date", "Amount", "Category", "Confidence",
                "Rule", "Bank date", "Bank amount", "Difference", "Explanation", "Source rows"};

        int rowIndex = 0;
        Row header = sheet.createRow(rowIndex++);
        for (int column = 0; column < headers.length; column++) {
            cell(header, column, headers[column], styles.header);
        }

        for (PayoutMatch match : matches) {
            Row row = sheet.createRow(rowIndex++);
            int column = 0;
            cell(row, column++, match.payout().payoutId(), styles.text);
            cell(row, column++, match.payout().displayName(), styles.text);
            if (detailed) {
                cell(row, column++, match.payout().accountMasked(), styles.text);
                cell(row, column++, match.payout().ifsc(), styles.text);
            }
            date(row, column++, match.payout().payoutDate(), styles.date);
            money(row, column++, match.payout().amount(), styles.money);
            if (detailed) {
                cell(row, column++, match.payout().status().name(), styles.text);
            }
            cell(row, column++, match.category().label(), styles.category(match.category()));
            cell(row, column++, match.confidence().label(), styles.text);
            cell(row, column++, match.ruleLabel(), styles.wrap);
            cell(row, column++, bankDates(match), styles.wrap);
            if (detailed) {
                cell(row, column++, bankNarration(match), styles.wrap);
            }
            money(row, column++, match.bankAmount(), styles.money);
            money(row, column, match.difference(), styles.money);
            column++;
            cell(row, column++, match.reason(), styles.wrap);
            cell(row, column, sourceRows(match), styles.text);
        }

        int[] widths = detailed
                ? new int[]{14, 20, 18, 14, 12, 14, 11, 16, 11, 30, 12, 50, 14, 12, 80, 12}
                : new int[]{14, 20, 12, 14, 16, 11, 30, 12, 14, 12, 80, 12};
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
        sheet.createFreezePane(0, 1);
        if (rowIndex > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, headers.length - 1));
        }
    }

    private void writeBankFindings(SXSSFWorkbook workbook, Styles styles, String name, List<BankFinding> findings) {
        Sheet sheet = workbook.createSheet(name);
        String[] headers = {"Bank date", "Narration", "Reference", "Debit", "Credit", "Balance",
                "Category", "Explanation", "Source row"};
        int rowIndex = 0;
        Row header = sheet.createRow(rowIndex++);
        for (int column = 0; column < headers.length; column++) {
            cell(header, column, headers[column], styles.header);
        }
        for (BankFinding finding : findings) {
            CanonicalBankTxn txn = finding.txn();
            Row row = sheet.createRow(rowIndex++);
            int column = 0;
            date(row, column++, txn.effectiveDate(), styles.date);
            cell(row, column++, txn.description(), styles.wrap);
            cell(row, column++, txn.chequeRef(), styles.text);
            money(row, column++, txn.debit() == null ? Money.ZERO : txn.debit(), styles.money);
            money(row, column++, txn.credit() == null ? Money.ZERO : txn.credit(), styles.money);
            money(row, column++, txn.balance() == null ? Money.ZERO : txn.balance(), styles.money);
            cell(row, column++, finding.category().label(), styles.category(finding.category()));
            cell(row, column++, finding.reason(), styles.wrap);
            cell(row, column, String.valueOf(txn.sourceRow()), styles.text);
        }
        int[] widths = {12, 60, 18, 14, 14, 14, 18, 80, 10};
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
        sheet.createFreezePane(0, 1);
        if (rowIndex > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, headers.length - 1));
        }
    }

    // ------------------------------------------------------------------ audit sheets

    private void writeQualityLog(SXSSFWorkbook workbook, Styles styles, ReconciliationResult result) {
        Sheet sheet = workbook.createSheet("Data Quality Log");
        String[] headers = {"Severity", "Source", "Row", "Field", "Message"};
        int rowIndex = 0;
        Row header = sheet.createRow(rowIndex++);
        for (int column = 0; column < headers.length; column++) {
            cell(header, column, headers[column], styles.header);
        }
        for (Issue issue : result.quality().issues()) {
            Row row = sheet.createRow(rowIndex++);
            cell(row, 0, issue.severity().name(), switch (issue.severity()) {
                case ERROR -> styles.fail;
                case WARN -> styles.warn;
                case INFO -> styles.text;
            });
            cell(row, 1, issue.source(), styles.text);
            cell(row, 2, issue.row() <= 0 ? "" : String.valueOf(issue.row()), styles.integer);
            cell(row, 3, issue.field() == null ? "" : issue.field(), styles.text);
            cell(row, 4, issue.message(), styles.wrap);
        }
        int[] widths = {10, 18, 8, 14, 110};
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
        sheet.createFreezePane(0, 1);
        if (rowIndex > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, headers.length - 1));
        }
    }

    private int mappingRows(Sheet sheet, int rowIndex, Styles styles,
                            com.chronon.recon.ingest.mapping.MappingProfile profile, ColumnMapping mapping) {
        for (String field : profile.fieldKeys()) {
            Integer column = mapping.index(field);
            Row row = sheet.createRow(rowIndex++);
            cell(row, 0, profile.target().label() + " • " + field, styles.text);
            cell(row, 1, profile.spec(field).isRequired() ? "Yes" : "No", styles.text);
            if (column == null) {
                cell(row, 2, "", styles.text);
                cell(row, 3, "(not mapped)", styles.text);
            } else {
                cell(row, 2, Math.round(mapping.score(field) * 100) + "%", styles.text);
                cell(row, 3, mapping.headerAt(column), styles.text);
            }
        }
        return rowIndex;
    }

    // ------------------------------------------------------------------ helpers

    private String bankDates(PayoutMatch match) {
        if (match.bankTxns().isEmpty()) {
            return "";
        }
        return String.join(", ", match.bankTxns().stream()
                .map(txn -> txn.effectiveDate() == null ? "unknown" : DATE.format(txn.effectiveDate()))
                .toList());
    }

    private String bankNarration(PayoutMatch match) {
        if (match.bankTxns().isEmpty()) {
            return "";
        }
        return String.join(" | ", match.bankTxns().stream()
                .map(CanonicalBankTxn::description)
                .toList());
    }

    private String sourceRows(PayoutMatch match) {
        StringBuilder text = new StringBuilder("payout row ").append(match.payout().sourceRow());
        if (!match.bankTxns().isEmpty()) {
            text.append("; bank row");
            if (match.bankTxns().size() > 1) {
                text.append("s");
            }
            text.append(" ").append(String.join(", ",
                    match.bankTxns().stream().map(txn -> String.valueOf(txn.sourceRow())).toList()));
        }
        return text.toString();
    }

    private void cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(sanitize(value));
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        if (value.startsWith("=") || value.startsWith("@")) {
            return "'" + value;
        }
        return value;
    }

    private void cell(Row row, int column, int value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void money(Row row, int column, Money value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? 0 : value.amount().doubleValue());
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void date(Row row, int column, LocalDate value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value == null) {
            cell.setCellValue("");
        } else {
            cell.setCellValue(value);
        }
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    /** All workbook styles created once; creating styles per cell exhausts POI's style limit. */
    private static final class Styles {
        final CellStyle title;
        final CellStyle section;
        final CellStyle header;
        final CellStyle text;
        final CellStyle wrap;
        final CellStyle bold;
        final CellStyle money;
        final CellStyle date;
        final CellStyle integer;
        final CellStyle pass;
        final CellStyle fail;
        final CellStyle warn;
        private final CellStyle matched;
        private final CellStyle review;
        private final CellStyle unmatched;

        Styles(SXSSFWorkbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            title = workbook.createCellStyle();
            title.setFont(titleFont);

            section = workbook.createCellStyle();
            section.setFont(boldFont);
            section.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            section.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            header = workbook.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setWrapText(true);

            text = bordered(workbook, null, false);
            wrap = bordered(workbook, null, true);
            bold = workbook.createCellStyle();
            bold.setFont(boldFont);

            money = bordered(workbook, "#,##0.00", false);
            money.setAlignment(HorizontalAlignment.RIGHT);
            date = bordered(workbook, "dd-mmm-yyyy", false);
            integer = bordered(workbook, "#,##0", false);
            integer.setAlignment(HorizontalAlignment.RIGHT);

            pass = workbook.createCellStyle();
            pass.cloneStyleFrom(text);
            pass.setFont(boldFont);
            pass.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            pass.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            fail = workbook.createCellStyle();
            fail.cloneStyleFrom(text);
            fail.setFont(boldFont);
            fail.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            fail.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            warn = workbook.createCellStyle();
            warn.cloneStyleFrom(text);
            warn.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            warn.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            matched = fill(workbook, IndexedColors.LIGHT_GREEN);
            review = fill(workbook, IndexedColors.LIGHT_YELLOW);
            unmatched = fill(workbook, IndexedColors.ROSE);
        }

        private static CellStyle bordered(SXSSFWorkbook workbook, String format, boolean wrapText) {
            CellStyle style = workbook.createCellStyle();
            style.setBorderBottom(BorderStyle.HAIR);
            style.setBorderTop(BorderStyle.HAIR);
            style.setBorderLeft(BorderStyle.HAIR);
            style.setBorderRight(BorderStyle.HAIR);
            style.setVerticalAlignment(VerticalAlignment.TOP);
            style.setWrapText(wrapText);
            if (format != null) {
                style.setDataFormat(workbook.createDataFormat().getFormat(format));
            }
            return style;
        }

        private static CellStyle fill(SXSSFWorkbook workbook, IndexedColors colour) {
            CellStyle style = bordered(workbook, null, false);
            style.setFillForegroundColor(colour.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }

        CellStyle category(MatchCategory category) {
            return switch (category) {
                case AUTO_MATCHED -> matched;
                case NEEDS_REVIEW -> review;
                default -> unmatched;
            };
        }
    }
}
