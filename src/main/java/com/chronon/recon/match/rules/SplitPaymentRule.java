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
import com.chronon.recon.match.Proposals;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One payout settled by several bank credits (bank limits, or finance splitting a large payout).
 *
 * <p>Bounded search: at most {@code maxSplitParts} credits out of at most 20 candidates, so the
 * worst case is a few thousand combinations rather than an exponential scan.</p>
 */
@Component
public class SplitPaymentRule implements MatchRule {

    public static final int ORDER = 40;
    private static final int MAX_CANDIDATES = 20;

    @Override
    public String id() {
        return "split-payment";
    }

    @Override
    public String label() {
        return "Payout split across credits";
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public Optional<MatchProposal> evaluate(CanonicalPayout payout, CandidateIndex index, MatchContext context) {
        MatchingProperties properties = context.properties();
        if (!properties.splitEnabled()) {
            return Optional.empty();
        }

        List<CanonicalBankTxn> candidates = index
                .creditsInWindow(properties.earliest(payout.payoutDate()), properties.latest(payout.payoutDate()))
                .stream()
                .filter(txn -> txn.credit().lt(payout.amount()))
                .sorted(Comparator
                        .comparing((CanonicalBankTxn txn) -> Corroboration.any(txn, payout) ? 0 : 1)
                        .thenComparing(CanonicalBankTxn::effectiveDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_CANDIDATES)
                .toList();

        if (candidates.size() < 2) {
            return Optional.empty();
        }

        Optional<List<CanonicalBankTxn>> subset =
                findSubset(candidates, 0, payout.amount(), properties.splitParts(), new ArrayList<>());
        if (subset.isEmpty()) {
            return Optional.empty();
        }

        List<CanonicalBankTxn> parts = subset.get();
        boolean referenced = parts.stream().anyMatch(txn -> referencesPayout(txn, payout));
        if (!referenced && parts.stream().noneMatch(txn -> Corroboration.any(txn, payout))) {
            return Optional.empty();
        }

        String detail = String.join(" + ", parts.stream()
                .map(txn -> txn.credit().display() + " on " + txn.effectiveDate())
                .toList());
        return Optional.of(Proposals.of(this, Confidence.MEDIUM, parts,
                "Payout settled in " + parts.size() + " credits: " + detail
                        + ", total " + payout.amount().display()
                        + ". The parts are tied to the payout by "
                        + (referenced ? "the payout reference in the narration"
                        : "the account, name or IFSC")
                        + ". Confirm the split before booking."));
    }

    private boolean referencesPayout(CanonicalBankTxn txn, CanonicalPayout payout) {
        return payout.referenceIds().stream()
                .anyMatch(reference -> com.chronon.recon.ingest.normalizer.ReferenceExtractor
                        .matches(reference, txn.tokens()));
    }

    private Optional<List<CanonicalBankTxn>> findSubset(List<CanonicalBankTxn> candidates, int start,
                                                        Money remaining, int slots, List<CanonicalBankTxn> chosen) {
        if (remaining.isZero() && chosen.size() >= 2) {
            return Optional.of(List.copyOf(chosen));
        }
        if (slots == 0 || start >= candidates.size()) {
            return Optional.empty();
        }
        for (int i = start; i < candidates.size(); i++) {
            CanonicalBankTxn txn = candidates.get(i);
            if (txn.credit().gt(remaining)) {
                continue;
            }
            chosen.add(txn);
            Optional<List<CanonicalBankTxn>> found =
                    findSubset(candidates, i + 1, remaining.minus(txn.credit()), slots - 1, chosen);
            if (found.isPresent()) {
                return found;
            }
            chosen.remove(chosen.size() - 1);
        }
        return Optional.empty();
    }
}
