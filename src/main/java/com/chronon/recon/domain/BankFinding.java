package com.chronon.recon.domain;

/** The disposition of a bank line that was not consumed by a payout match. */
public record BankFinding(
        CanonicalBankTxn txn,
        MatchCategory category,
        String reason
) {
    public BankFinding {
        if (category != MatchCategory.UNMATCHED_BANK_DEBIT
                && category != MatchCategory.NON_PAYOUT
                && category != MatchCategory.NEEDS_REVIEW) {
            throw new IllegalArgumentException("Unsupported bank finding category: " + category);
        }
    }
}
