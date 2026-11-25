package com.chronon.recon.match;

import com.chronon.recon.domain.CanonicalPayout;

import java.util.Optional;

/**
 * A matching rule in the reconciliation pipeline.
 *
 * <p>Each rule evaluates candidates in the bank statement, assigns a confidence level,
 * and produces a plain-English explanation that can be audited by a human.</p>
 */
public interface MatchRule {

    String id();

    String label();

    /** Lower runs first and wins ties. */
    int order();

    Optional<MatchProposal> evaluate(CanonicalPayout payout, CandidateIndex index, MatchContext context);
}
