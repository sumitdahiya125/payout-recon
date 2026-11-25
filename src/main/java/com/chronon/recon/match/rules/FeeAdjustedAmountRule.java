package com.chronon.recon.match.rules;

import com.chronon.recon.config.MatchingProperties;
import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.CanonicalPayout;
import com.chronon.recon.domain.Confidence;
import com.chronon.recon.domain.Money;
import com.chronon.recon.match.CandidateIndex;
import com.chronon.recon.match.Corroboration;
import com.chronon.recon.match.MatchContext;
import com.chronon.recon.match.MatchProposal;
import com.chronon.recon.match.MatchRule;
import com.chronon.recon.match.MatchText;
import com.chronon.recon.match.Proposals;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Amount is slightly short (or over) but within the fee/rounding tolerance, with corroboration.
 *
 * <p>Never auto-booked: a fee-adjusted amount is plausible, not certain, so it goes to review with
 * the exact difference spelled out.</p>
 */
@Component
public class FeeAdjustedAmountRule implements MatchRule {

    public static final int ORDER = 30;

    @Override
    public String id() {
        return "fee-adjusted-amount";
    }

    @Override
    public String label() {
        return "Amount within fee tolerance";
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public Optional<MatchProposal> evaluate(CanonicalPayout payout, CandidateIndex index, MatchContext context) {
        MatchingProperties properties = context.properties();
        Money tolerance = properties.tolerance(payout.amount());
        if (tolerance.isZero()) {
            return Optional.empty();
        }

        List<CanonicalBankTxn> candidates = index.creditsWithin(payout.amount(), tolerance).stream()
                .filter(txn -> properties.withinWindow(payout.payoutDate(), txn.effectiveDate()))
                .filter(txn -> Corroboration.any(txn, payout))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        CanonicalBankTxn best = candidates.stream()
                .min(Comparator.comparing(txn -> txn.credit().minus(payout.amount()).abs()))
                .orElseThrow();
        Money difference = best.credit().minus(payout.amount());

        String direction = difference.isNegative() ? "short" : "over";
        return Optional.of(Proposals.of(this, Confidence.MEDIUM, best,
                "Closest credit of " + best.credit().display() + " is " + difference.abs().display()
                        + " " + direction + " against the payout amount (" + MatchText.relativeDate(
                        payout.payoutDate(), best.effectiveDate()) + "), inside the fee tolerance of "
                        + tolerance.display() + "; narration contains " + Corroboration.describe(best, payout)
                        + ". Confirm whether the difference is a transfer fee."));
    }
}
