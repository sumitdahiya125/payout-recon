package com.chronon.recon.domain;

import java.util.List;

/**
 * The outcome for one payout, including the plain-English explanation finance needs in order to
 * verify the result without reading code.
 *
 * @param ruleId    machine id of the rule that produced the outcome, e.g. "reference"
 * @param ruleLabel human label, e.g. "Payout reference found in narration"
 * @param reason    full explanation, e.g. "Narration contains P-1042; credit 12,500.00 equals
 *                  payout amount; posted 1 day after payout date."
 */
public record PayoutMatch(
        CanonicalPayout payout,
        List<CanonicalBankTxn> bankTxns,
        MatchCategory category,
        Confidence confidence,
        String ruleId,
        String ruleLabel,
        String reason,
        Money bankAmount
) {

    public PayoutMatch {
        bankTxns = bankTxns == null ? List.of() : List.copyOf(bankTxns);
        bankAmount = bankAmount == null ? Money.ZERO : bankAmount;
    }

    public static PayoutMatch unmatched(CanonicalPayout payout, String ruleId, String ruleLabel, String reason) {
        return new PayoutMatch(payout, List.of(), MatchCategory.UNMATCHED_PAYOUT, Confidence.NONE,
                ruleId, ruleLabel, reason, Money.ZERO);
    }

    public Money difference() {
        return payout.amount().minus(bankAmount);
    }

    public boolean hasBankTxns() {
        return !bankTxns.isEmpty();
    }
}
