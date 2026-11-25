package com.chronon.recon.match;

import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.Confidence;
import com.chronon.recon.domain.Money;

import java.util.List;

/** Small factory so every rule builds proposals the same way, including the signed bank total. */
public final class Proposals {

    private Proposals() {
    }

    public static MatchProposal of(MatchRule rule, Confidence confidence, List<CanonicalBankTxn> txns, String reason) {
        Money total = txns.stream()
                .map(CanonicalBankTxn::signedAmount)
                .reduce(Money.ZERO, Money::plus);
        return new MatchProposal(rule.id(), rule.label(), rule.order(), confidence, txns, total, reason);
    }

    public static MatchProposal of(MatchRule rule, Confidence confidence, CanonicalBankTxn txn, String reason) {
        return of(rule, confidence, List.of(txn), reason);
    }
}
