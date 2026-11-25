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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Strongest rule: the payout reference appears in the bank narration.
 *
 * <p>Even here the amount must match exactly and the date must fall inside the window before the
 * match is booked automatically; a reference alone is not proof that the money arrived.</p>
 */
@Component
public class ExactReferenceRule implements MatchRule {

    public static final int ORDER = 10;

    @Override
    public String id() {
        return "reference";
    }

    @Override
    public String label() {
        return "Payout reference in narration";
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public Optional<MatchProposal> evaluate(CanonicalPayout payout, CandidateIndex index, MatchContext context) {
        MatchingProperties properties = context.properties();

        Map<Integer, CanonicalBankTxn> hits = new LinkedHashMap<>();
        List<String> matchedRefs = new ArrayList<>();
        for (String reference : payout.referenceIds()) {
            boolean found = false;
            for (CanonicalBankTxn txn : index.byReference(reference)) {
                if (txn.isCredit()) {
                    hits.putIfAbsent(txn.sourceRow(), txn);
                    found = true;
                }
            }
            if (found) {
                matchedRefs.add(reference);
            }
        }
        if (hits.isEmpty()) {
            return Optional.empty();
        }

        // A specific reference that points at exactly one credit of the right amount is the
        // strongest evidence available. This is what lets a duplicate payout id be resolved by
        // its distinct claim ids.
        for (String reference : payout.referenceIds()) {
            List<CanonicalBankTxn> exactForReference = index.byReference(reference).stream()
                    .filter(CanonicalBankTxn::isCredit)
                    .filter(txn -> txn.credit().equals(payout.amount()))
                    .filter(txn -> properties.withinWindow(payout.payoutDate(), txn.effectiveDate()))
                    .toList();
            if (exactForReference.size() == 1) {
                CanonicalBankTxn txn = exactForReference.get(0);
                return Optional.of(Proposals.of(this, Confidence.HIGH, txn,
                        highConfidenceReason(payout, txn, reference)));
            }
        }

        List<CanonicalBankTxn> credits = List.copyOf(hits.values());
        String refText = matchedRefs.isEmpty() ? payout.payoutId() : matchedRefs.get(0);

        List<CanonicalBankTxn> exact = credits.stream()
                .filter(txn -> txn.credit().equals(payout.amount()))
                .toList();

        if (exact.size() == 1) {
            CanonicalBankTxn txn = exact.get(0);
            return Optional.of(Proposals.of(this, Confidence.MEDIUM, txn,
                    "Reference " + refText + " and amount " + txn.credit().display()
                            + " match, but the credit posted " + MatchText.relativeDate(payout.payoutDate(), txn.effectiveDate())
                            + ", outside the allowed window of -" + properties.windowBefore()
                            + "/+" + properties.windowAfter() + " days."));
        }

        if (exact.size() > 1) {
            return Optional.of(Proposals.of(this, Confidence.MEDIUM, exact,
                    exact.size() + " bank credits each reference " + refText + " and equal the payout amount of "
                            + payout.amount().display() + " (" + amounts(exact)
                            + "). The tool cannot tell which one settled this payout."));
        }

        CanonicalBankTxn closest = credits.stream()
                .min(Comparator.comparing(txn -> txn.credit().minus(payout.amount()).abs()))
                .orElseThrow();
        Money difference = closest.credit().minus(payout.amount());
        boolean inWindow = properties.withinWindow(payout.payoutDate(), closest.effectiveDate());

        if (properties.withinTolerance(payout.amount(), closest.credit()) && inWindow) {
            return Optional.of(Proposals.of(this, Confidence.MEDIUM, closest,
                    "Reference " + refText + " matches and the credit of " + closest.credit().display()
                            + " differs from the payout amount by " + difference.abs().display()
                            + ", within the fee/rounding tolerance of "
                            + properties.tolerance(payout.amount()).display() + "."));
        }

        String windowNote = inWindow ? "" : " The credit also posted "
                + MatchText.relativeDate(payout.payoutDate(), closest.effectiveDate())
                + ", outside the date window.";
        return Optional.of(Proposals.of(this, Confidence.LOW, closest,
                "Reference " + refText + " matches but the credit of " + closest.credit().display()
                        + " differs from the payout amount by " + difference.abs().display() + "." + windowNote));
    }

    private String highConfidenceReason(CanonicalPayout payout, CanonicalBankTxn txn, String refText) {
        StringBuilder reason = new StringBuilder()
                .append("Narration contains payout reference ").append(refText)
                .append(" and the credit of ").append(txn.credit().display())
                .append(" equals the payout amount, posted ")
                .append(MatchText.relativeDate(payout.payoutDate(), txn.effectiveDate()));
        if (Corroboration.accountTail(txn, payout)) {
            reason.append("; the account ending ").append(payout.accountTail()).append(" also appears in the narration");
        }
        return reason.append(".").toString();
    }

    private String amounts(List<CanonicalBankTxn> txns) {
        return String.join(" + ", txns.stream().map(txn -> txn.credit().display()).toList());
    }
}
