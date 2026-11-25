package com.chronon.recon.demo;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates a realistic pair of files with known, documented anomalies.
 *
 * <p>Two things matter here. First, the data is deliberately messy the way bank exports are messy:
 * preamble rows, merged cells, mixed date formats, amounts stored as text, non-breaking spaces,
 * blank rows and a total row. Second, every planted anomaly is recorded in the manifest, so the
 * README and the tests can prove the tool detects it instead of merely claiming so.</p>
 */
@Component
public class SampleDataGenerator {

    public enum Expectation {
        AUTO, REVIEW, UNMATCHED
    }

    public record ExpectedPayout(String payoutId, Expectation expectation, String note) {
    }

    public record Generated(Path payoutFile, Path bankFile, Path manifestFile,
                            List<ExpectedPayout> expectations, int bankLineCount) {
    }

    private static final String[] NAMES = {
            "Rahul Sharma", "Priya Nair", "Arjun Mehta", "Sneha Iyer", "Vikram Rao",
            "Ananya Gupta", "Karthik Menon", "Divya Pillai", "Rohan Das", "Meera Joshi"
    };
    private static final String[] ACCOUNTS = {
            "XXXXXX4412", "XXXXXX7781", "XXXXXX3320", "XXXXXX9014", "XXXXXX5567",
            "XXXXXX2208", "XXXXXX6635", "XXXXXX8890", "XXXXXX1145", "XXXXXX7723"
    };
    private static final String[] IFSC = {
            "HDFC0000123", "ICIC0004567", "HDFC0000123", "SBIN0007788", "HDFC0000123",
            "AXIS0003344", "HDFC0000123", "ICIC0004567", "HDFC0000123", "SBIN0007788"
    };
    private static final String[] EMP_IDS = {
            "EMP-101", "EMP-102", "EMP-103", "EMP-104", "EMP-105",
            "EMP-106", "EMP-107", "EMP-108", "EMP-109", "EMP-110"
    };

    private static final LocalDate BASE_DATE = LocalDate.of(2025, 3, 3);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DMY_SHORT = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final DateTimeFormatter DMMMY = DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Generated generate(Path directory, long seed) throws IOException {
        Files.createDirectories(directory);

        List<PayoutSpec> payouts = new ArrayList<>();
        List<BankRow> bank = new ArrayList<>();
        List<ExpectedPayout> expectations = new ArrayList<>();

        // ---------------------------------------------------------------- clean reference matches
        long[] cleanAmounts = {1500, 2350, 4200, 3750, 5100, 6800, 7400, 2900, 9600, 10500, 8600, 4900};
        String[] cleanNarrations = {
                "NEFT CR-HDFC0000123-RAHUL SHARMA-PO-1001",
                "IMPS/PO-1002/PRIYA NAIR/XXXXXX7781",
                "UPI/PAYOUT/PO-1003",
                "PAYOUT PO-1004 ARJUN MEHTA",
                "NEFT CR PO1005",
                "IMPS-PO-1006-EXPENSE REIMB",
                "NEFT CR-HDFC0000123-SNEHA IYER-PO-1007",
                "UPI/PO-1008",
                "NEFT/PO-1009/VIKRAM RAO",
                "PAYOUT/PO-1010",
                "NEFT CR PO-1011 ANANYA GUPTA",
                "IMPS PO-1012"
        };
        for (int i = 0; i < cleanAmounts.length; i++) {
            int employee = i % NAMES.length;
            int day = i % 20;
            String id = "PO-" + (1001 + i);
            payouts.add(new PayoutSpec(id, employee, cleanAmounts[i], day, "PAID",
                    "CLM-" + (7001 + i), Expectation.AUTO,
                    "Clean payment with the payout reference in the narration"));
            bank.add(BankRow.credit(BASE_DATE.plusDays(day), cleanNarrations[i], "", cleanAmounts[i]));
            expectations.add(new ExpectedPayout(id, Expectation.AUTO,
                    "Narration " + i + " carries the payout reference and an exact amount"));
        }

        // ---------------------------------------------------------------- amount + account/name only
        long[] noRefAmounts = {12000, 16750, 18900};
        String[] noRefNarrations = {
                "NEFT CR-RAHUL SHARMA-EXPENSE REIMB",
                "NEFT CR-XXXXXX7781-EXPENSE REIMB",
                "NEFT CR-ARJUN MEHTA-EXPENSE REIMB"
        };
        for (int i = 0; i < noRefAmounts.length; i++) {
            int employee = i;
            int day = 5 + i;
            String id = "PO-" + (1013 + i);
            payouts.add(new PayoutSpec(id, employee, noRefAmounts[i], day, "PAID",
                    "CLM-" + (7101 + i), Expectation.AUTO,
                    "No payout reference; matched by exact amount plus name/account in the narration"));
            bank.add(BankRow.credit(BASE_DATE.plusDays(day), noRefNarrations[i], "", noRefAmounts[i]));
            expectations.add(new ExpectedPayout(id, Expectation.AUTO,
                    "No reference; account or beneficiary name corroborates the amount"));
        }

        // 1016: exact amount with the account tail only, no name and no reference
        payouts.add(new PayoutSpec("PO-1016", 8, 13500, 8, "PAID", "CLM-7103", Expectation.AUTO,
                "Matched by exact amount and account tail only"));
        bank.add(BankRow.credit(BASE_DATE.plusDays(8), "NEFT CR XXXXXX1145", "", 13500));
        expectations.add(new ExpectedPayout("PO-1016", Expectation.AUTO,
                "Account tail in the narration, exact amount"));

        // ---------------------------------------------------------------- fee short
        payouts.add(new PayoutSpec("PO-1017", 2, 8400, 9, "PAID", "CLM-7201", Expectation.REVIEW,
                "Bank credited 10.00 less than the payout (transfer fee)"));
        bank.add(BankRow.credit(BASE_DATE.plusDays(9), "NEFT CR-ARJUN MEHTA-PO-1017", "", 8390));
        expectations.add(new ExpectedPayout("PO-1017", Expectation.REVIEW,
                "Reference matches but the amount is 10.00 short"));

        payouts.add(new PayoutSpec("PO-1018", 7, 15000, 10, "PAID", "CLM-7202", Expectation.REVIEW,
                "Bank credited 25.00 less than the payout, name only in the narration"));
        bank.add(BankRow.credit(BASE_DATE.plusDays(10), "NEFT CR-DIVYA PILLAI-EXPENSE REIMB", "", 14975));
        expectations.add(new ExpectedPayout("PO-1018", Expectation.REVIEW,
                "Amount short by 25.00, no reference, name corroborated"));

        // ---------------------------------------------------------------- split payment
        payouts.add(new PayoutSpec("PO-1019", 5, 30000, 12, "PAID", "CLM-7301", Expectation.REVIEW,
                "Paid in two parts because of a per-transaction limit"));
        bank.add(BankRow.credit(BASE_DATE.plusDays(13), "IMPS PO-1019 PART 1", "", 18000));
        bank.add(BankRow.credit(BASE_DATE.plusDays(14), "IMPS PO-1019 PART 2", "", 12000));
        expectations.add(new ExpectedPayout("PO-1019", Expectation.REVIEW,
                "Two credits of 18,000.00 and 12,000.00 sum to the payout"));

        // ---------------------------------------------------------------- outside the date window
        payouts.add(new PayoutSpec("PO-1020", 8, 12500, 10, "PAID", "CLM-7401", Expectation.REVIEW,
                "Credited six days after the payout date, outside the window"));
        bank.add(BankRow.credit(BASE_DATE.plusDays(16), "NEFT CR-ROHAN DAS-PO-1020", "", 12500));
        expectations.add(new ExpectedPayout("PO-1020", Expectation.REVIEW,
                "Reference and amount match but the credit is six days late"));

        // ---------------------------------------------------------------- never paid / failed
        payouts.add(new PayoutSpec("PO-1021", 3, 4550, 6, "PAID", "CLM-7501", Expectation.UNMATCHED,
                "Register says paid but no bank credit exists"));
        expectations.add(new ExpectedPayout("PO-1021", Expectation.UNMATCHED,
                "No bank credit at all"));

        payouts.add(new PayoutSpec("PO-1022", 9, 3000, 7, "FAILED", "CLM-7502", Expectation.UNMATCHED,
                "Failed payout - excluded from matching by status"));
        expectations.add(new ExpectedPayout("PO-1022", Expectation.UNMATCHED,
                "Status FAILED, so no credit is expected"));

        // ---------------------------------------------------------------- duplicate payout id, paid twice
        payouts.add(new PayoutSpec("PO-1023", 1, 5600, 8, "PAID", "CLM-9001", Expectation.AUTO,
                "Duplicate payout id in the register (row 1 of 2)"));
        payouts.add(new PayoutSpec("PO-1023", 1, 5600, 8, "PAID", "CLM-9002", Expectation.AUTO,
                "Duplicate payout id in the register (row 2 of 2)"));
        bank.add(BankRow.credit(BASE_DATE.plusDays(8), "NEFT CR-PRIYA NAIR-CLM-9001", "", 5600));
        bank.add(BankRow.credit(BASE_DATE.plusDays(9), "NEFT CR-PRIYA NAIR-CLM-9002", "", 5600));
        expectations.add(new ExpectedPayout("PO-1023", Expectation.AUTO,
                "Each duplicate row is identified by its own claim id"));
        expectations.add(new ExpectedPayout("PO-1023", Expectation.AUTO,
                "Each duplicate row is identified by its own claim id"));

        // ---------------------------------------------------------------- ambiguous amount
        payouts.add(new PayoutSpec("PO-1024", 4, 7800, 18, "PAID", "CLM-7601", Expectation.REVIEW,
                "Same amount, date and no narration evidence as PO-1024B"));
        payouts.add(new PayoutSpec("PO-1024B", 6, 7800, 18, "PAID", "CLM-7602", Expectation.REVIEW,
                "Same amount, date and no narration evidence as PO-1024"));
        bank.add(BankRow.credit(BASE_DATE.plusDays(18), "NEFT CR EXPENSE REIMB", "", 7800));
        expectations.add(new ExpectedPayout("PO-1024", Expectation.REVIEW,
                "One credit of 7,800.00 could belong to either payout"));
        expectations.add(new ExpectedPayout("PO-1024B", Expectation.REVIEW,
                "One credit of 7,800.00 could belong to either payout"));

        // ---------------------------------------------------------------- returned NEFT
        payouts.add(new PayoutSpec("PO-1025", 9, 9800, 15, "PAID", "CLM-7701", Expectation.AUTO,
                "Paid, then returned by the bank two days later"));
        bank.add(BankRow.credit(BASE_DATE.plusDays(15), "NEFT CR-MEERA JOSHI-PO-1025", "", 9800));
        bank.add(BankRow.debit(BASE_DATE.plusDays(17), "NEFT RETURN-PO-1025-ACCOUNT CLOSED", "", 9800));
        expectations.add(new ExpectedPayout("PO-1025", Expectation.AUTO,
                "Credit matches; the separate return debit is flagged for review"));

        // ---------------------------------------------------------------- unexplained short credit
        payouts.add(new PayoutSpec("PO-1026", 0, 6400, 21, "PAID", "CLM-7801", Expectation.UNMATCHED,
                "Bank credited 6,385.00 with no reference and no corroboration"));
        bank.add(BankRow.credit(BASE_DATE.plusDays(22), "NEFT CR", "", 6385));
        expectations.add(new ExpectedPayout("PO-1026", Expectation.UNMATCHED,
                "Nothing ties the 6,385.00 credit to this payout"));

        // ---------------------------------------------------------------- claim-id match
        payouts.add(new PayoutSpec("PO-1027", 2, 11200, 22, "PAID", "CLM-9500", Expectation.AUTO,
                "Narration carries the claim id, not the payout id"));
        bank.add(BankRow.credit(BASE_DATE.plusDays(22), "NEFT CLM-9500", "", 11200));
        expectations.add(new ExpectedPayout("PO-1027", Expectation.AUTO,
                "Claim id in the narration identifies the payout"));

        // ---------------------------------------------------------------- account tail only, no name
        payouts.add(new PayoutSpec("PO-1028", 4, 14200, 23, "PAID", "CLM-9501", Expectation.AUTO,
                "Matched by exact amount and account tail with no name or reference"));
        bank.add(BankRow.credit(BASE_DATE.plusDays(23), "NEFT CR XXXXXX5567", "", 14200));
        expectations.add(new ExpectedPayout("PO-1028", Expectation.AUTO,
                "Account tail in the narration, exact amount"));

        // ---------------------------------------------------------------- non-payout bank activity
        bank.add(BankRow.credit(BASE_DATE.plusDays(1), "NEFT CR-SALARY MARCH 2025", "", 85000));
        bank.add(BankRow.credit(BASE_DATE.plusDays(2), "INT CREDIT Q4", "", 1240));
        bank.add(BankRow.debit(BASE_DATE.plusDays(2), "SMS CHARGES", "", 118));
        bank.add(BankRow.debit(BASE_DATE.plusDays(3), "GST PAYMENT FEB", "", 450));
        bank.add(BankRow.debit(BASE_DATE.plusDays(4), "ATM CASH WITHDRAWAL", "", 10000));
        bank.add(BankRow.debit(BASE_DATE.plusDays(5), "NEFT DR-ACME SUPPLIES-VENDOR PAYMENT", "", 25000));
        bank.add(BankRow.credit(BASE_DATE.plusDays(6), "UPI/REFUND/ORDER 5512", "", 3200));
        bank.add(BankRow.debit(BASE_DATE.plusDays(7), "FD SWEEP OUT", "", 50000));
        bank.add(BankRow.debit(BASE_DATE.plusDays(11), "TDS PAYMENT", "", 2200));
        bank.add(BankRow.debit(BASE_DATE.plusDays(13), "INSURANCE PREMIUM MARCH", "", 7500));
        bank.add(BankRow.debit(BASE_DATE.plusDays(19), "NEFT DR-UNKNOWN BENEFICIARY", "", 4500));

        Path payoutFile = directory.resolve("payout_register.xlsx");
        Path bankFile = directory.resolve("bank_statement.xlsx");
        Path manifestFile = directory.resolve("expected_anomalies.md");

        writePayoutRegister(payoutFile, payouts);
        writeBankStatement(bankFile, bank);
        Files.writeString(manifestFile, manifest(seed, payouts, expectations));

        return new Generated(payoutFile, bankFile, manifestFile, expectations, bank.size());
    }

    // ------------------------------------------------------------------ payout register

    private void writePayoutRegister(Path file, List<PayoutSpec> payouts) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file)) {
            Sheet sheet = workbook.createSheet("Payout Register");
            CellStyle money = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            money.setDataFormat(format.getFormat("#,##0.00"));
            CellStyle date = workbook.createCellStyle();
            date.setDataFormat(format.getFormat("dd/mm/yyyy"));

            String[] headers = {"Payout ID", "Employee ID", "Employee Name", "Bank Account (masked)",
                    "IFSC", "Amount", "Payout Date", "Claim IDs", "Status"};
            Row header = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                header.createCell(column).setCellValue(headers[column]);
            }

            int rowIndex = 1;
            for (int i = 0; i < payouts.size(); i++) {
                PayoutSpec spec = payouts.get(i);
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(spec.id());
                row.createCell(1).setCellValue(EMP_IDS[spec.employee()]);
                row.createCell(2).setCellValue(NAMES[spec.employee()]);
                row.createCell(3).setCellValue(ACCOUNTS[spec.employee()]);
                row.createCell(4).setCellValue(IFSC[spec.employee()]);

                if (i % 4 == 0) {
                    row.createCell(5).setCellValue("₹ " + grouped(spec.amount()));
                } else {
                    Cell amount = row.createCell(5);
                    amount.setCellValue(spec.amount());
                    amount.setCellStyle(money);
                }

                if (i % 5 == 3) {
                    row.createCell(6).setCellValue(DMY.format(BASE_DATE.plusDays(spec.dayOffset())));
                } else {
                    Cell dateCell = row.createCell(6);
                    dateCell.setCellValue(BASE_DATE.plusDays(spec.dayOffset()));
                    dateCell.setCellStyle(date);
                }

                row.createCell(7).setCellValue(spec.claimIds());
                row.createCell(8).setCellValue(spec.status());

                if (i == 5) {
                    sheet.createRow(rowIndex++);
                }
            }

            Row total = sheet.createRow(rowIndex);
            total.createCell(0).setCellValue("Total");
            Cell totalAmount = total.createCell(5);
            totalAmount.setCellValue(payouts.stream().mapToLong(PayoutSpec::amount).sum());
            totalAmount.setCellStyle(money);

            for (int column = 0; column < headers.length; column++) {
                sheet.setColumnWidth(column, (column == 2 ? 22 : 18) * 256);
            }
            sheet.createFreezePane(0, 1);
            workbook.write(out);
        }
    }

    // ------------------------------------------------------------------ bank statement

    private void writeBankStatement(Path file, List<BankRow> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file)) {
            Sheet sheet = workbook.createSheet("Statement");
            CellStyle money = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            money.setDataFormat(format.getFormat("#,##0.00"));
            CellStyle date = workbook.createCellStyle();
            date.setDataFormat(format.getFormat("dd/mm/yyyy"));

            Row bankName = sheet.createRow(0);
            bankName.createCell(0).setCellValue("HDFC BANK LTD");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));
            sheet.createRow(1).createCell(0).setCellValue("Statement of Account");
            sheet.createRow(2).createCell(0).setCellValue("Account No: XXXXXX4412");
            sheet.createRow(3).createCell(0).setCellValue("Statement Period: 01/03/2025 to 31/03/2025");
            sheet.createRow(4);

            String[] headers = {"Txn Date", "Value Date", "Narration", "Cheque/Ref No",
                    "Debit", "Credit", "Balance"};
            Row header = sheet.createRow(5);
            for (int column = 0; column < headers.length; column++) {
                header.createCell(column).setCellValue(headers[column]);
            }

            BigDecimal balance = new BigDecimal("500000.00");
            int rowIndex = 6;
            for (int i = 0; i < rows.size(); i++) {
                BankRow spec = rows.get(i);
                balance = balance.add(BigDecimal.valueOf(spec.credit()))
                        .subtract(BigDecimal.valueOf(spec.debit()));

                Row row = sheet.createRow(rowIndex++);
                writeDate(row, 0, spec.date(), i, date);
                if (i % 7 == 3) {
                    // value date deliberately missing
                } else {
                    writeDate(row, 1, spec.date(), i + 1, date);
                }

                String narration = spec.narration();
                if (i == 3) {
                    narration = narration.replace(" ", "\u00A0").replace("-", "  -  ");
                }
                row.createCell(2).setCellValue(narration);
                row.createCell(3).setCellValue(spec.reference());

                if (spec.debit() > 0) {
                    writeAmount(row, 4, spec.debit(), i, true, money);
                }
                if (spec.credit() > 0) {
                    writeAmount(row, 5, spec.credit(), i, false, money);
                }

                Cell balanceCell = row.createCell(6);
                balanceCell.setCellValue(balance.doubleValue());
                balanceCell.setCellStyle(money);

                if (i % 9 == 8) {
                    sheet.createRow(rowIndex++);
                }
            }

            Row closing = sheet.createRow(rowIndex++);
            closing.createCell(2).setCellValue("Closing Balance");
            Cell closingBalance = closing.createCell(6);
            closingBalance.setCellValue(balance.doubleValue());
            closingBalance.setCellStyle(money);

            Row total = sheet.createRow(rowIndex);
            total.createCell(2).setCellValue("Total");
            total.createCell(4).setCellValue(rows.stream().mapToLong(BankRow::debit).sum());
            total.createCell(5).setCellValue(rows.stream().mapToLong(BankRow::credit).sum());

            int[] widths = {12, 12, 62, 16, 14, 14, 16};
            for (int column = 0; column < widths.length; column++) {
                sheet.setColumnWidth(column, widths[column] * 256);
            }
            sheet.createFreezePane(0, 6);
            workbook.write(out);
        }
    }

    private void writeDate(Row row, int column, LocalDate value, int variant, CellStyle dateStyle) {
        switch (variant % 4) {
            case 0 -> {
                Cell cell = row.createCell(column);
                cell.setCellValue(value);
                cell.setCellStyle(dateStyle);
            }
            case 1 -> row.createCell(column).setCellValue(DMY.format(value));
            case 2 -> row.createCell(column).setCellValue(DMMMY.format(value));
            default -> row.createCell(column).setCellValue(ISO.format(value));
        }
    }

    private void writeAmount(Row row, int column, long amount, int variant, boolean debit, CellStyle money) {
        switch (variant % 5) {
            case 0 -> {
                Cell cell = row.createCell(column);
                cell.setCellValue(amount);
                cell.setCellStyle(money);
            }
            case 1 -> row.createCell(column).setCellValue("₹ " + grouped(amount));
            case 2 -> row.createCell(column).setCellValue(grouped(amount) + (debit ? " Dr" : " Cr"));
            case 3 -> row.createCell(column).setCellValue(debit
                    ? "(" + grouped(amount) + ")"
                    : grouped(amount));
            default -> {
                Cell cell = row.createCell(column);
                cell.setCellValue(amount);
                cell.setCellStyle(money);
            }
        }
    }

    private String grouped(long amount) {
        return String.format(Locale.US, "%,.2f", (double) amount);
    }

    // ------------------------------------------------------------------ manifest

    private String manifest(long seed, List<PayoutSpec> payouts, List<ExpectedPayout> expectations) {
        StringBuilder text = new StringBuilder();
        text.append("# Sample data - planted anomalies\n\n");
        text.append("Generated with seed `").append(seed).append("`.\n\n");
        text.append("Files: `payout_register.xlsx` (")
                .append(payouts.size()).append(" payout rows) and `bank_statement.xlsx`.\n\n");
        text.append("| Payout | Expected outcome | What was planted |\n");
        text.append("|---|---|---|\n");
        for (ExpectedPayout expected : expectations) {
            text.append("| ").append(expected.payoutId())
                    .append(" | ").append(expected.expectation())
                    .append(" | ").append(expected.note())
                    .append(" |\n");
        }
        text.append("\n## Bank-only lines\n\n");
        text.append("- 10 non-payout movements (salary, interest, charges, GST, ATM, vendor, refund, "
                + "FD sweep, TDS, insurance).\n");
        text.append("- 1 unexplained debit of 4,500.00 that should appear as an unmatched bank debit.\n");
        text.append("- 1 return debit for PO-1025 that should appear in the review list.\n");
        text.append("- 1 unexplained credit of 6,385.00 that should appear in the review list.\n\n");
        text.append("## Data mess baked into the files\n\n");
        text.append("- Bank statement has a 5-row preamble and a merged title cell.\n");
        text.append("- Dates appear as Excel dates, dd/MM/yyyy, d-MMM-yyyy and yyyy-MM-dd.\n");
        text.append("- Amounts appear as numbers, '₹ 1,234.00', '1,234.00 Cr', '1,234.00 Dr' and '(1,234.00)'.\n");
        text.append("- Blank rows, a missing value date, a non-breaking-space narration, a total row "
                + "and a closing balance row.\n");
        text.append("- Payout register has a blank row and a trailing Total row.\n");
        return text.toString();
    }

    // ------------------------------------------------------------------ specs

    private record PayoutSpec(String id, int employee, long amount, int dayOffset, String status,
                              String claimIds, Expectation expectation, String note) {
    }

    private record BankRow(LocalDate date, String narration, String reference, long debit, long credit) {

        static BankRow credit(LocalDate date, String narration, String reference, long amount) {
            return new BankRow(date, narration, reference, 0, amount);
        }

        static BankRow debit(LocalDate date, String narration, String reference, long amount) {
            return new BankRow(date, narration, reference, amount, 0);
        }
    }
}
