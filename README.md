# Payout Reconciliation Tool

Senior Software Engineer Take-Home Assignment for Chronon: an automated, explainable reconciliation engine that matches Chronon's weekly payout register (`payout_register.xlsx`) against the company's bank statement (`bank_statement.xlsx`).

---

## Quick Start & Execution

Requires **Java 21+** and Maven (wrapper included for both Windows and Linux/macOS).

### 1. Build and Run Tests
```bash
# Linux / macOS:
./mvnw clean test

# Windows:
.\mvnw.cmd clean test
```

### 2. Package the Application
```bash
# Linux / macOS:
./mvnw clean package

# Windows:
.\mvnw.cmd clean package
```

### 3. Run the Web Application
```bash
# Linux / macOS:
./mvnw spring-boot:run

# Windows:
.\mvnw.cmd spring-boot:run

# Or run the packaged jar:
java -jar target/payout-recon-1.0.0.jar
```
Open [http://localhost:8080](http://localhost:8080) in your browser. Finance users simply upload the payout register and bank statement. The tool auto-detects column layouts dynamically, displays the live tie-out dashboard, and provides the downloadable Excel report.

---

## Architectural Highlights & Invariants

1. **Dual Financial Invariant (Zero Money Leakage)**:
   * **Payout partition**: $\text{Register Total} - \text{Auto-Matched} - \text{Needs Review} - \text{Unmatched Payouts} = 0.00$
   * **Bank partition**: $(\text{Bank Credits} - \text{Bank Debits}) - (\text{Matched Bank} + \text{Review Bank} + \text{Unmatched Debits} + \text{Non-Payout}) = 0.00$
   * **Tie-Out Sign-Off**: The report's tie-out is marked `PASSED` **only** if both residuals are zero **and** zero fatal data-quality parsing errors occurred.
2. **Conservative Auto-Booking**:
   * Only `HIGH` confidence matches (exact amount corroborated by reference or account tail/name within date window) are booked automatically.
   * Ambiguous matches, fee deductions, and split payments are routed to `NEEDS_REVIEW` with plain-English rationales.
3. **One-to-One Settlement Guarantee**:
   * One bank credit settles at most one payout. If two payouts contest the same credit, the contested line is sent to `NEEDS_REVIEW` to prevent double-counting.
4. **Automatic Column Detection & Declarative Mapping**:
   * Statements are automatically scored and mapped against registered profiles with zero manual profile selection.
   * Adding support for a new bank statement requires **zero Java code changes**: drop a declarative YAML profile into `src/main/resources/profiles/`.
5. **Linear Matching Scalability**:
   * Uses an in-memory token and amount hash index (`CandidateIndex`) to achieve $O(N + M)$ linear matching rather than $O(N \times M)$ nested scanning.
6. **Streaming Report Generation**:
   * Uses Apache POI `SXSSFWorkbook(200)` with disk compression to stream 4 consolidated workbook sheets without heap exhaustion.

### System Architecture & Data Flow

The application is structured as a modular pipeline:
1. **Ingest & Mapping**: `WorkbookReader` inspects candidate header rows and matches them against bank YAML profiles (`ProfileRegistry`), handling merged cells and messy preambles.
2. **Canonical Parsing**: `PayoutRegisterParser` and `BankStatementParser` convert raw rows into strongly typed records (`CanonicalPayout`, `CanonicalBankTxn`, `Money`).
3. **Heuristic Match Engine**: `Matcher` indexes bank credits by token and amount (`CandidateIndex`), evaluating rules sequentially (exact reference, amount + account/date corroboration, fee adjustments, and multi-credit splits).
4. **Financial Tie-Out & Reporting**: `SummaryCalculator` checks dual zero-residual invariants, and `ReportWriter` streams a 4-sheet Excel report via POI SXSSF.

---

## Matching Approach & Threshold Rationales

The matching pipeline is governed by configurable properties in `application.yml` under `recon.matching`:

| Parameter | Default Value | Financial & Operational Rationale |
|---|---|---|
| **Date Window Before** | `-1` day | Accommodates UTC/IST timezone discrepancies and transactions initiated before midnight but value-dated on the next day. |
| **Date Window After** | `+3` days | Accommodates NEFT/RTGS batch clearance cycles, interbank settlement cutoffs, weekends, and national bank holidays. |
| **Fee Tolerance (Fixed)** | `₹25.00` | Covers standard corporate banking transfer charge slabs (e.g. IMPS/NEFT fees ranging from ₹2.50 to ₹15.00 + 18% GST). |
| **Fee Tolerance (Rate)** | `0.50%` | Covers percentage-based gateway or remittance deductions for larger payouts. Whichever is greater between fixed and rate tolerance applies. |
| **Split Payments** | Max `3` parts | Real-world expense splits rarely exceed 2–3 tranches; this strictly limits subset-sum branch search to $< 1,140$ iterations, preventing combinatorial explosion. |
| **Auto-Match Confidence** | `HIGH` only | Guarantees that only corroborated matches with exact amounts are booked automatically. Heuristic guesses never auto-book. |

---

## Real-World Messy Data Handling

| Case / Anomaly | Real-World Appearance | Handling Strategy |
|---|---|---|
| **Preamble Rows** | Bank logos, account metadata, disclaimers before table | `ColumnResolver.bestHeader()` scans top candidate rows and ranks header scores. |
| **Merged Cells** | Bank statement headers or section dividers spanning columns | `WorkbookReader` inspects merged regions and propagates top-left values. |
| **Non-Breaking Spaces** | `NEFT\u00A0CR` in narration text | `TextNormalizer.clean()` replaces control characters and `\u00A0` with ASCII spaces. |
| **Diverse Dates** | `dd/MM/yyyy`, `d-MMM-yyyy`, `yyyy-MM-dd`, Excel serials | `DateParser` detects day-first vs month-first order based on column evidence. |
| **Money Notation** | `₹ 1,234.00`, `1,234.00 Cr`, accounting negatives `(1,234.00)` | `MoneyParser` parses accounting parentheses, currency symbols, and Cr/Dr flags into `Money` (`BigDecimal` scale 2). |
| **Trailing Rows** | Summary rows, total rows, closing balance lines | Detected and skipped by blank-row and non-tabular filters. |
| **Non-Payout Movements** | Salaries, taxes, vendor payouts, ATM, GST, FD sweep | `BankLineClassifier` filters operational movements into a separate audit sheet. |
| **Reversals / Return Debits** | Debits referencing payout IDs | Flagged for review so finance verifies whether the reimbursement bounced. |
| **Status Exclusions** | Payout status `FAILED` or `CANCELLED` | Excluded from matching since no bank credit was expected; reported separately. |

---

## Output Report Structure (`reconciliation-report.xlsx`)

The generated report contains **4 streamlined, purpose-built sheets** equipped with Excel AutoFilters:

1. **Summary & Tie-Out**: High-level tie-out dashboard showing payout register totals, bank totals, mathematical tie-out status card (`PASSED` / `FAILED`), match quality breakdown, and the embedded column mapping table used for ingestion.
2. **Payout Reconciliation**: Consolidated ledger of all payouts with category styling (`Matched (Auto)`, `Needs Review`, `Unmatched (Payout)`), confidence level, rule applied, bank dates, bank narrations, difference, and full plain-English explanations. Includes Excel AutoFilter on row 1 for instant 1-click filtering by finance ops.
3. **Bank Statement Activity**: Consolidated ledger of all bank movements requiring audit or review (`Needs Review`, `Non-payout activity`, `Unmatched (Bank debit)`), with narration, cheque/reference, debit/credit/balance, plain-English reasons, and source row tracing. Pre-configured with Excel AutoFilter.
4. **Data Quality Log**: Complete audit trail of skipped, coerced, or suspicious cells with exact source row, field, severity (`ERROR`, `WARN`, `INFO`), and message.

---

## Verified Sample Run Results

Executing against `data/payout_register.xlsx` and `data/bank_statement.xlsx` (seeded anomalies documented in [expected_anomalies.md](file:///C:/Users/sumit/Document/repo/data/expected_anomalies.md)):

```text
Payouts            : 30 (21 matched, 6 review, 3 unmatched)
Register total     : 270600.00
Bank lines         : 40 (matched: 21, review: 8, non-payout: 10, unmatched debits: 1)
Tie-out            : PASSED (payout difference 0.00, bank difference 0.00)
Data quality       : 0 errors, 1 warnings
Report             : ...\reconciliation-report.xlsx
```

* **Auto-Matched (21)**: PO-1001 to PO-1016, PO-1023 (duplicate resolved via distinct claim IDs), PO-1025, PO-1027, PO-1028.
* **Needs Review (6)**: PO-1017 (fee ₹10 short), PO-1018 (fee ₹25 short), PO-1019 (split into 2 credits), PO-1020 (6 days late), PO-1024 / PO-1024B (contested credit).
* **Unmatched (3)**: PO-1021 (missing credit), PO-1022 (status FAILED), PO-1026 (unlinked credit).

---

## Adding Support for a New Bank (Zero Code Changes)

To support statements from a new bank (e.g. Axis Bank), create `src/main/resources/profiles/bank-axis.yml`:

```yaml
id: bank-axis-statement
description: Axis Bank corporate statement export
target: BANK_STATEMENT
order: 15
fields:
  txnDate:
    type: DATE
    required: true
    aliases: ["Tran Date", "Transaction Date", "Date"]
  narration:
    type: STRING
    required: true
    aliases: ["Particulars", "Transaction Remarks", "Description"]
  debit:
    type: MONEY
    aliases: ["Debit", "DR", "Withdrawal"]
  credit:
    type: MONEY
    aliases: ["Credit", "CR", "Deposit"]
  balance:
    type: MONEY
    aliases: ["Balance", "Net Balance"]
nonPayoutKeywords:
  - "salary"
  - "interest"
  - "gst"
  - "vendor"
```

Restart or deploy; the application discovers the new profile automatically via `ProfileRegistry`.

---

## Security & Robustness Measures

* **Formula / CSV Injection Defense**: String values written to Excel cells in `ReportWriter` are sanitized against leading formula operators (`=`, `@`), neutralizing DDE exploits.
* **Path Traversal Protection**: Filenames uploaded via Web UI and REST API are sanitized with `StringUtils.cleanPath()` and `Path.getFileName()`.
* **File Upload Bounds**: Spring multipart upload limits configured to `50MB` in `application.yml` to prevent Denial of Service (DoS) memory exhaustion.
* **Fail-Fast DOM Guard Rail**: Ingest caps in-memory DOM extraction at 200,000 rows with an immediate `IngestionException`, preventing silent truncation.

---

## Production Design for 500,000+ Rows

The current implementation is intentionally sized for take-home file sizes using DOM parsing (`WorkbookFactory`). To process 500k+ rows in production:

1. **SAX Event Ingestion**: Replace POI DOM `WorkbookFactory` with POI `XSSFReader` SAX streaming, parsing XML rows in lightweight chunks without retaining cell objects in heap.
2. **Embedded SQL Staging**: Stream parsed payouts and bank lines directly into an embedded high-performance columnar engine (DuckDB or SQLite).
3. **Set-Based Candidate Joins**: Replace in-memory hash loops with indexed SQL candidate joins:
   ```sql
   SELECT p.id, b.id, ...
   FROM payouts p
   JOIN bank_txns b ON b.credit = p.amount
   WHERE b.txn_date BETWEEN p.payout_date - INTERVAL 1 DAY AND p.payout_date + INTERVAL 3 DAY;
   ```
4. **Targeted Rule Evaluation**: Execute the heuristic rule pipeline only over the joined candidate pairs.
5. **Disk-Streamed Output**: Keep the report SXSSF-streamed and write directly to a temporary file rather than buffering `byte[]` in memory.

---

## Two-Week Roadmap

1. **Direct CSV / Fixed-Width Parsing**: Add streaming CSV parsers (Apache Commons CSV) sharing the same profile mapping system.
2. **Database-Backed Rule Configuration**: Store matching thresholds per corporate tenant in PostgreSQL, enabling runtime configuration updates without application restarts.
3. **Automated SFTP / S3 Ingestion**: Polling jobs to pull daily bank statement exports automatically from corporate banking SFTP servers.
4. **Web UI Review Actions**: Add interactive buttons in the web UI for finance managers to approve or reject review exceptions and post settlement confirmations back to Chronon via webhook.
5. **Synthetic 500k Performance Test Harness**: Continuous integration benchmark tracking parsing, matching, and report generation times on 500,000 synthetic rows.
