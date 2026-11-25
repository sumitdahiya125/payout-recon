package com.chronon.recon.match;

import com.chronon.recon.config.MatchingProperties;
import com.chronon.recon.domain.BankFinding;
import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.CanonicalPayout;
import com.chronon.recon.domain.Confidence;
import com.chronon.recon.domain.MatchCategory;
import com.chronon.recon.domain.Money;
import com.chronon.recon.domain.PayoutMatch;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs the rule pipeline and enforces the two invariants that make the output trustworthy:
 *
 * <ol>
 *   <li>Only a HIGH-confidence proposal is booked automatically.</li>
 *   <li>A bank line settles at most one payout. Contested lines are pushed to review, never
 *       silently double-counted.</li>
 * </ol>
 */
@Component
public class Matcher {

    private final List<MatchRule> rules;
    private final BankLineClassifier classifier;

    public Matcher(List<MatchRule> rules, BankLineClassifier classifier) {
        this.rules = rules.stream().sorted(Comparator.comparingInt(MatchRule::order)).toList();
        this.classifier = classifier;
    }

    public List<MatchRule> rules() {
        return rules;
    }

    public MatchOutcome match(List<CanonicalPayout> payouts,
                              List<CanonicalBankTxn> transactions,
                              MatchingProperties properties,
                              List<String> nonPayoutKeywords) {

        CandidateIndex index = new CandidateIndex(transactions);
        MatchContext context = new MatchContext(properties);

        Map<Integer, PayoutMatch> results = new LinkedHashMap<>();
        List<Candidate> candidates = new ArrayList<>();

        for (CanonicalPayout payout : payouts) {
            if (!payout.isPayable()) {
                results.put(payout.sourceRow(), new PayoutMatch(
                        payout, List.of(), MatchCategory.UNMATCHED_PAYOUT, Confidence.NONE,
                        "status", "Excluded by status",
                        "Payout status is " + payout.status() + ", so no bank credit was expected.",
                        Money.ZERO));
                continue;
            }

            List<MatchProposal> proposals = rules.stream()
                    .map(rule -> rule.evaluate(payout, index, context))
                    .flatMap(Optional::stream)
                    .sorted(bestFirst(payout))
                    .toList();

            if (proposals.isEmpty()) {
                results.put(payout.sourceRow(), PayoutMatch.unmatched(payout, "no-candidate", "No matching credit",
                        "No bank credit in the statement matches this payout's reference, amount, account or name "
                                + "within the allowed date window."));
            } else {
                candidates.add(new Candidate(payout, proposals.get(0)));
            }
        }

        Set<Integer> claimedRows = new HashSet<>();
        Map<Integer, String> claimedBy = new LinkedHashMap<>();

        List<Candidate> highConfidence = candidates.stream()
                .filter(candidate -> candidate.proposal().confidence().isHigh())
                .sorted(assignmentOrder())
                .toList();

        for (Candidate candidate : highConfidence) {
            Optional<Integer> conflict = candidate.proposal().txns().stream()
                    .map(CanonicalBankTxn::sourceRow)
                    .filter(claimedRows::contains)
                    .findFirst();

            if (conflict.isPresent()) {
                String owner = claimedBy.get(conflict.get());
                results.put(candidate.payout().sourceRow(),
                        review(candidate, candidate.proposal(), "Not booked: bank line "
                                + conflict.get() + " is already used by payout " + owner
                                + ". Two payouts claim the same credit, so one of them is wrong."));
                continue;
            }

            claim(candidate.proposal(), claimedRows, claimedBy, candidate.payout().payoutId());
            results.put(candidate.payout().sourceRow(), new PayoutMatch(
                    candidate.payout(),
                    candidate.proposal().txns(),
                    MatchCategory.AUTO_MATCHED,
                    Confidence.HIGH,
                    candidate.proposal().ruleId(),
                    candidate.proposal().ruleLabel(),
                    candidate.proposal().reason(),
                    candidate.proposal().bankAmount()));
        }

        List<Candidate> review = candidates.stream()
                .filter(candidate -> !candidate.proposal().confidence().isHigh())
                .sorted(assignmentOrder())
                .toList();

        for (Candidate candidate : review) {
            List<CanonicalBankTxn> contested = candidate.proposal().txns().stream()
                    .filter(txn -> claimedRows.contains(txn.sourceRow()))
                    .toList();

            if (!contested.isEmpty()) {
                String owners = contested.stream()
                        .map(txn -> claimedBy.get(txn.sourceRow()))
                        .distinct()
                        .collect(Collectors.joining(", "));
                results.put(candidate.payout().sourceRow(),
                        review(candidate, candidate.proposal(), "Candidate bank line is already used by payout "
                                + owners + ". " + candidate.proposal().reason()));
                continue;
            }

            claim(candidate.proposal(), claimedRows, claimedBy, candidate.payout().payoutId());
            results.put(candidate.payout().sourceRow(), review(candidate, candidate.proposal(),
                    candidate.proposal().reason()));
        }

        List<BankFinding> findings = new ArrayList<>();
        List<PayoutMatch> orderedMatches = new ArrayList<>();
        for (CanonicalPayout payout : payouts) {
            orderedMatches.add(results.get(payout.sourceRow()));
        }

        for (CanonicalBankTxn txn : transactions) {
            if (!claimedRows.contains(txn.sourceRow())) {
                findings.add(classifier.classify(txn, orderedMatches, payouts, properties, nonPayoutKeywords));
            }
        }

        return new MatchOutcome(orderedMatches, findings);
    }

    private void claim(MatchProposal proposal, Set<Integer> claimedRows, Map<Integer, String> claimedBy,
                       String payoutId) {
        for (CanonicalBankTxn txn : proposal.txns()) {
            claimedRows.add(txn.sourceRow());
            claimedBy.put(txn.sourceRow(), payoutId);
        }
    }

    private PayoutMatch review(Candidate candidate, MatchProposal proposal, String reason) {
        return new PayoutMatch(
                candidate.payout(),
                proposal.txns(),
                MatchCategory.NEEDS_REVIEW,
                proposal.confidence() == Confidence.HIGH ? Confidence.MEDIUM : proposal.confidence(),
                proposal.ruleId(),
                proposal.ruleLabel(),
                reason,
                proposal.bankAmount());
    }

    private Comparator<MatchProposal> bestFirst(CanonicalPayout payout) {
        return Comparator
                .comparingInt((MatchProposal proposal) -> -proposal.confidence().rank())
                .thenComparingInt(MatchProposal::order)
                .thenComparingLong(proposal -> dayDistance(payout, proposal))
                .thenComparing(MatchProposal::reason);
    }

    private Comparator<Candidate> assignmentOrder() {
        return Comparator
                .comparingInt((Candidate candidate) -> candidate.proposal().order())
                .thenComparing(candidate -> candidate.payout().payoutDate())
                .thenComparing(candidate -> candidate.payout().payoutId())
                .thenComparingInt(candidate -> candidate.payout().sourceRow());
    }

    private long dayDistance(CanonicalPayout payout, MatchProposal proposal) {
        if (proposal.txns().isEmpty() || proposal.txns().get(0).effectiveDate() == null) {
            return Long.MAX_VALUE;
        }
        return Math.abs(ChronoUnit.DAYS.between(payout.payoutDate(), proposal.txns().get(0).effectiveDate()));
    }

    private record Candidate(CanonicalPayout payout, MatchProposal proposal) {
    }
}
