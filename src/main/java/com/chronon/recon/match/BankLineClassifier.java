package com.chronon.recon.match;

import com.chronon.recon.config.MatchingProperties;
import com.chronon.recon.domain.BankFinding;
import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.CanonicalPayout;
import com.chronon.recon.domain.MatchCategory;
import com.chronon.recon.domain.PayoutMatch;
import com.chronon.recon.ingest.normalizer.ReferenceExtractor;
import com.chronon.recon.ingest.normalizer.TextNormalizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Explains every bank line that no payout claimed. A bank line is never left unlabelled: it is
 * either a payout-related exception (review), an unexplained debit, or genuinely unrelated activity.
 */
@Component
public class BankLineClassifier {

    public BankFinding classify(CanonicalBankTxn txn,
                                List<PayoutMatch> matches,
                                List<CanonicalPayout> payouts,
                                MatchingProperties properties,
                                List<String> nonPayoutKeywords) {

        Optional<CanonicalPayout> referenced = payouts.stream()
                .filter(payout -> payout.referenceIds().stream()
                        .anyMatch(reference -> ReferenceExtractor.matches(reference, txn.tokens())))
                .findFirst();

        if (referenced.isPresent()) {
            CanonicalPayout payout = referenced.get();
            if (txn.isDebit()) {
                return new BankFinding(txn, MatchCategory.NEEDS_REVIEW,
                        "Debit references payout " + payout.payoutId()
                                + ", so it looks like a return or reversal of that credit. Confirm before "
                                + "treating the payout as settled.");
            }
            boolean alreadySettled = matches.stream()
                    .filter(match -> match.category() == MatchCategory.AUTO_MATCHED)
                    .anyMatch(match -> match.payout().payoutId().equals(payout.payoutId()));
            return new BankFinding(txn, MatchCategory.NEEDS_REVIEW,
                    alreadySettled
                            ? "Credit references payout " + payout.payoutId()
                            + ", which is already settled by another credit. Possible duplicate payment."
                            : "Credit references payout " + payout.payoutId()
                            + " but was not selected as its match. Confirm manually.");
        }

        if (txn.isDebit()) {
            Optional<String> debitKeyword = findKeyword(txn, nonPayoutKeywords);
            if (debitKeyword.isPresent()) {
                return new BankFinding(txn, MatchCategory.NON_PAYOUT,
                        "Debit narration contains '" + debitKeyword.get()
                                + "', which is unrelated to payout reimbursements.");
            }
            return new BankFinding(txn, MatchCategory.UNMATCHED_BANK_DEBIT,
                    "Debit is not linked to any payout in the register.");
        }

        Optional<String> keyword = findKeyword(txn, nonPayoutKeywords);
        if (keyword.isPresent()) {
            return new BankFinding(txn, MatchCategory.NON_PAYOUT,
                    "Narration contains '" + keyword.get() + "', which is unrelated to payout reimbursements.");
        }

        Optional<CanonicalPayout> sameAmount = matches.stream()
                .filter(match -> match.category() == MatchCategory.UNMATCHED_PAYOUT)
                .map(PayoutMatch::payout)
                .filter(payout -> properties.withinTolerance(payout.amount(), txn.credit()))
                .findFirst();

        if (sameAmount.isPresent()) {
            return new BankFinding(txn, MatchCategory.NEEDS_REVIEW,
                    "Credit of " + txn.credit().display() + " matches the amount of unmatched payout "
                            + sameAmount.get().payoutId() + " but has no reference, account or name evidence.");
        }

        return new BankFinding(txn, MatchCategory.NON_PAYOUT,
                "No payout reference and no matching payout amount; treated as unrelated bank activity.");
    }

    private Optional<String> findKeyword(CanonicalBankTxn txn, List<String> keywords) {
        String narration = TextNormalizer.fold(txn.foldedText()).toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            String folded = TextNormalizer.fold(keyword).toLowerCase(Locale.ROOT);
            if (!folded.isEmpty() && narration.contains(folded)) {
                return Optional.of(keyword);
            }
        }
        return Optional.empty();
    }
}
