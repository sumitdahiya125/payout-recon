package com.chronon.recon.domain;

/** The disposition of a payout or bank line in the reconciliation. */
public enum MatchCategory {
    /** High confidence: reference or exact amount + account + date. No human needed. */
    AUTO_MATCHED("Matched", 1),
    /** A plausible candidate exists but the evidence is not strong enough to book silently. */
    NEEDS_REVIEW("Needs review", 2),
    /** A payout with no acceptable bank counterpart. */
    UNMATCHED_PAYOUT("Unmatched payout", 3),
    /** A bank debit that is not linked to any payout. */
    UNMATCHED_BANK_DEBIT("Unmatched bank debit", 4),
    /** Bank activity that is legitimately unrelated to payouts (salary, interest, charges...). */
    NON_PAYOUT("Non-payout bank activity", 5);

    private final String label;
    private final int sortOrder;

    MatchCategory(String label, int sortOrder) {
        this.label = label;
        this.sortOrder = sortOrder;
    }

    public String label() {
        return label;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public boolean isPayoutCategory() {
        return this == AUTO_MATCHED || this == NEEDS_REVIEW || this == UNMATCHED_PAYOUT;
    }
}
