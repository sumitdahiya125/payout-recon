# Sample data - planted anomalies

Generated with seed `20250301`.

Files: `payout_register.xlsx` (30 payout rows) and `bank_statement.xlsx`.

| Payout | Expected outcome | What was planted |
|---|---|---|
| PO-1001 | AUTO | Narration 0 carries the payout reference and an exact amount |
| PO-1002 | AUTO | Narration 1 carries the payout reference and an exact amount |
| PO-1003 | AUTO | Narration 2 carries the payout reference and an exact amount |
| PO-1004 | AUTO | Narration 3 carries the payout reference and an exact amount |
| PO-1005 | AUTO | Narration 4 carries the payout reference and an exact amount |
| PO-1006 | AUTO | Narration 5 carries the payout reference and an exact amount |
| PO-1007 | AUTO | Narration 6 carries the payout reference and an exact amount |
| PO-1008 | AUTO | Narration 7 carries the payout reference and an exact amount |
| PO-1009 | AUTO | Narration 8 carries the payout reference and an exact amount |
| PO-1010 | AUTO | Narration 9 carries the payout reference and an exact amount |
| PO-1011 | AUTO | Narration 10 carries the payout reference and an exact amount |
| PO-1012 | AUTO | Narration 11 carries the payout reference and an exact amount |
| PO-1013 | AUTO | No reference; account or beneficiary name corroborates the amount |
| PO-1014 | AUTO | No reference; account or beneficiary name corroborates the amount |
| PO-1015 | AUTO | No reference; account or beneficiary name corroborates the amount |
| PO-1016 | AUTO | Account tail in the narration, exact amount |
| PO-1017 | REVIEW | Reference matches but the amount is 10.00 short |
| PO-1018 | REVIEW | Amount short by 25.00, no reference, name corroborated |
| PO-1019 | REVIEW | Two credits of 18,000.00 and 12,000.00 sum to the payout |
| PO-1020 | REVIEW | Reference and amount match but the credit is six days late |
| PO-1021 | UNMATCHED | No bank credit at all |
| PO-1022 | UNMATCHED | Status FAILED, so no credit is expected |
| PO-1023 | AUTO | Each duplicate row is identified by its own claim id |
| PO-1023 | AUTO | Each duplicate row is identified by its own claim id |
| PO-1024 | REVIEW | One credit of 7,800.00 could belong to either payout |
| PO-1024B | REVIEW | One credit of 7,800.00 could belong to either payout |
| PO-1025 | AUTO | Credit matches; the separate return debit is flagged for review |
| PO-1026 | UNMATCHED | Nothing ties the 6,385.00 credit to this payout |
| PO-1027 | AUTO | Claim id in the narration identifies the payout |
| PO-1028 | AUTO | Account tail in the narration, exact amount |

## Bank-only lines

- 10 non-payout movements (salary, interest, charges, GST, ATM, vendor, refund, FD sweep, TDS, insurance).
- 1 unexplained debit of 4,500.00 that should appear as an unmatched bank debit.
- 1 return debit for PO-1025 that should appear in the review list.
- 1 unexplained credit of 6,385.00 that should appear in the review list.

## Data mess baked into the files

- Bank statement has a 5-row preamble and a merged title cell.
- Dates appear as Excel dates, dd/MM/yyyy, d-MMM-yyyy and yyyy-MM-dd.
- Amounts appear as numbers, '₹ 1,234.00', '1,234.00 Cr', '1,234.00 Dr' and '(1,234.00)'.
- Blank rows, a missing value date, a non-breaking-space narration, a total row and a closing balance row.
- Payout register has a blank row and a trailing Total row.
