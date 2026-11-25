package com.chronon.recon.match;

import com.chronon.recon.domain.BankFinding;
import com.chronon.recon.domain.PayoutMatch;

import java.util.List;

/** Everything the matcher produced: one row per payout, plus a disposition for every loose bank line. */
public record MatchOutcome(List<PayoutMatch> payoutMatches, List<BankFinding> bankFindings) {

    public MatchOutcome {
        payoutMatches = payoutMatches == null ? List.of() : List.copyOf(payoutMatches);
        bankFindings = bankFindings == null ? List.of() : List.copyOf(bankFindings);
    }
}
