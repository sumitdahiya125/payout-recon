package com.chronon.recon.match.rules;

import com.chronon.recon.config.MatchingProperties;
import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.CanonicalPayout;
import com.chronon.recon.domain.Confidence;
import com.chronon.recon.match.CandidateIndex;
import com.chronon.recon.match.Corroboration;
import com.chronon.recon.match.MatchContext;
import com.chronon.recon.match.MatchProposal;
import com.chronon.recon.match.MatchRule;
import com.chronon.recon.match.MatchText;
import com.chronon.recon.match.Proposals;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Exact amount, inside the date window, plus independent corroboration from the narration.
 *
 * <p>Corroboration is what separates a booking from a guess: the account tail, the beneficiary name
 * or the IFSC must appear. A unique credit with the right amount and date but no corroboration is
 * sent to review instead.</p>
 */
@Component
public class AmountAccountDateRule implements MatchRule {

    public static final int ORDER = 20;

    @Override
    public String id() {
        return "amount-account-date";
    }

    @Override
    public String label() {
        return "Exact amount, date and account/name";
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public Optional<MatchProposal> evaluate(CanonicalPayout payout, CandidateIndex index, MatchContext context) {
        MatchingProperties properties = context.properties();
        List<CanonicalBankTxn> candidates = index.creditsExactly(payout.amount()).stream()
                .filter(txn -> properties.withinWindow(payout.payoutDate(), txn.effectiveDate()))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        List<CanonicalBankTxn> corroborated = candidates.stream()
                .filter(txn -> Corroboration.any(txn, payout))
                .toList();

        if (corroborated.size() == 1) {
            CanonicalBankTxn txn = corroborated.get(0);
            return Optional.of(Proposals.of(this, Confidence.HIGH, txn,
                    "No payout reference in the narration, but the credit of " + txn.credit().display()
                            + " equals the payout amount exactly, posted "
                            + MatchText.relativeDate(payout.payoutDate(), txn.effectiveDate())
                            + ", and the narration contains " + Corroboration.describe(txn, payout) + "."));
        }

        if (corroborated.size() > 1) {
            return Optional.of(Proposals.of(this, Confidence.MEDIUM, corroborated,
                    corroborated.size() + " credits match the amount and date and each has some corroboration ("
                            + String.join("; ", corroborated.stream()
                            .map(txn -> txn.credit().display() + " on " + txn.effectiveDate()
                                    + " via " + Corroboration.describe(txn, payout)).toList())
                            + "). Ambiguous."));
        }

        if (candidates.size() == 1) {
            CanonicalBankTxn txn = candidates.get(0);
            return Optional.of(Proposals.of(this, Confidence.MEDIUM, txn,
                    "Exactly one credit of " + txn.credit().display() + " matches the payout amount and posted "
                            + MatchText.relativeDate(payout.payoutDate(), txn.effectiveDate())
                            + ", but the narration has no account, name or IFSC to confirm it."));
        }

        return Optional.of(Proposals.of(this, Confidence.MEDIUM, candidates,
                candidates.size() + " credits match the payout amount and date window with no unique "
                        + "account or name corroboration. Choose the correct one manually."));
    }
}
