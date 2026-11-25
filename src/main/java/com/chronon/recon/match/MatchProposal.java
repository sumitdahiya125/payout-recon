package com.chronon.recon.match;

import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.Confidence;
import com.chronon.recon.domain.Money;

import java.util.List;

/**
 * A candidate explanation produced by a rule.
 *
 * @param order rule priority; lower wins when confidence is equal
 */
public record MatchProposal(
        String ruleId,
        String ruleLabel,
        int order,
        Confidence confidence,
        List<CanonicalBankTxn> txns,
        Money bankAmount,
        String reason
) {

    public MatchProposal {
        txns = txns == null ? List.of() : List.copyOf(txns);
        bankAmount = bankAmount == null ? Money.ZERO : bankAmount;
    }
}
