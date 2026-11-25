package com.chronon.recon.domain;

import java.util.List;
import java.util.Map;

/**
 * Totals for a reconciliation run.
 *
 * <p>The two residuals are the point of the whole exercise: they must both be zero, which proves
 * that every rupee in the payout register and every rupee in the bank statement has been
 * accounted for exactly once.</p>
 */
public record Summary(
        int payoutCount,
        Money registerTotal,
        int excludedPayoutCount,
        Money excludedPayoutTotal,

        List<CategoryTotal> payoutTotals,
        int matchedCount,
        Money matchedTotal,
        int reviewCount,
        Money reviewTotal,
        int unmatchedPayoutCount,
        Money unmatchedPayoutTotal,

        int bankLineCount,
        Money bankCreditTotal,
        Money bankDebitTotal,
        List<CategoryTotal> bankTotals,
        int matchedBankLineCount,
        Money matchedBankTotal,
        int reviewBankLineCount,
        Money reviewBankTotal,
        int unmatchedDebitCount,
        Money unmatchedDebitTotal,
        int nonPayoutCount,
        Money nonPayoutTotal,

        Money payoutResidual,
        Money bankResidual,
        boolean tieOutOk,

        Map<String, Integer> reviewReasons,
        int issueCount,
        int errorCount,
        int warningCount
) {

    public Money bankNetTotal() {
        return bankCreditTotal.minus(bankDebitTotal);
    }

    public double autoMatchRate() {
        return payoutCount == 0 ? 0d : (matchedCount * 100.0) / payoutCount;
    }
}
