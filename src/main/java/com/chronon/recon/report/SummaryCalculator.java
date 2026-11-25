package com.chronon.recon.report;

import com.chronon.recon.domain.BankFinding;
import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.CanonicalPayout;
import com.chronon.recon.domain.CategoryTotal;
import com.chronon.recon.domain.MatchCategory;
import com.chronon.recon.domain.Money;
import com.chronon.recon.domain.PayoutMatch;
import com.chronon.recon.domain.Summary;
import com.chronon.recon.match.MatchOutcome;
import com.chronon.recon.domain.quality.DataQualityLog;
import com.chronon.recon.domain.quality.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Produces the totals that must tie back to the input files.
 *
 * <p>Both partitions are checked to the rupee: every payout amount lands in exactly one payout
 * category, and every bank line's signed amount lands in exactly one bank category. A non-zero
 * residual means the tool lost or double-counted money, and the report says so loudly.</p>
 */
@Component
public class SummaryCalculator {

    public Summary compute(List<CanonicalPayout> payouts,
                           List<CanonicalBankTxn> transactions,
                           MatchOutcome outcome,
                           DataQualityLog quality) {

        Money registerTotal = payouts.stream().map(CanonicalPayout::amount).reduce(Money.ZERO, Money::plus);
        int excludedCount = 0;
        Money excludedTotal = Money.ZERO;
        for (CanonicalPayout payout : payouts) {
            if (!payout.isPayable()) {
                excludedCount++;
                excludedTotal = excludedTotal.plus(payout.amount());
            }
        }

        Map<MatchCategory, Integer> payoutCounts = new EnumMap<>(MatchCategory.class);
        Map<MatchCategory, Money> payoutAmounts = new EnumMap<>(MatchCategory.class);
        Map<String, Integer> reviewReasons = new LinkedHashMap<>();

        for (PayoutMatch match : outcome.payoutMatches()) {
            MatchCategory category = match.category();
            payoutCounts.merge(category, 1, Integer::sum);
            payoutAmounts.merge(category, match.payout().amount(), Money::plus);
            if (category == MatchCategory.NEEDS_REVIEW) {
                reviewReasons.merge(match.ruleLabel(), 1, Integer::sum);
            }
        }

        Money matchedTotal = payoutAmounts.getOrDefault(MatchCategory.AUTO_MATCHED, Money.ZERO);
        Money reviewTotal = payoutAmounts.getOrDefault(MatchCategory.NEEDS_REVIEW, Money.ZERO);
        Money unmatchedTotal = payoutAmounts.getOrDefault(MatchCategory.UNMATCHED_PAYOUT, Money.ZERO);

        List<CategoryTotal> payoutTotals = new ArrayList<>();
        for (MatchCategory category : List.of(MatchCategory.AUTO_MATCHED, MatchCategory.NEEDS_REVIEW,
                MatchCategory.UNMATCHED_PAYOUT)) {
            payoutTotals.add(new CategoryTotal(category,
                    payoutCounts.getOrDefault(category, 0),
                    payoutAmounts.getOrDefault(category, Money.ZERO)));
        }

        Money payoutResidual = registerTotal.minus(matchedTotal).minus(reviewTotal).minus(unmatchedTotal);

        // ---- bank side: each transaction counted exactly once, in priority order ----
        Set<Integer> counted = new HashSet<>();
        int matchedLines = 0;
        Money matchedBank = Money.ZERO;
        int reviewLines = 0;
        Money reviewBank = Money.ZERO;

        for (PayoutMatch match : outcome.payoutMatches()) {
            if (match.category() != MatchCategory.AUTO_MATCHED) {
                continue;
            }
            for (CanonicalBankTxn txn : match.bankTxns()) {
                if (counted.add(txn.sourceRow())) {
                    matchedLines++;
                    matchedBank = matchedBank.plus(txn.signedAmount());
                }
            }
        }
        for (PayoutMatch match : outcome.payoutMatches()) {
            if (match.category() != MatchCategory.NEEDS_REVIEW) {
                continue;
            }
            for (CanonicalBankTxn txn : match.bankTxns()) {
                if (counted.add(txn.sourceRow())) {
                    reviewLines++;
                    reviewBank = reviewBank.plus(txn.signedAmount());
                }
            }
        }

        int unmatchedDebitCount = 0;
        Money unmatchedDebitTotal = Money.ZERO;
        int nonPayoutCount = 0;
        Money nonPayoutTotal = Money.ZERO;

        for (BankFinding finding : outcome.bankFindings()) {
            if (!counted.add(finding.txn().sourceRow())) {
                continue;
            }
            switch (finding.category()) {
                case UNMATCHED_BANK_DEBIT -> {
                    unmatchedDebitCount++;
                    unmatchedDebitTotal = unmatchedDebitTotal.plus(finding.txn().signedAmount());
                }
                case NON_PAYOUT -> {
                    nonPayoutCount++;
                    nonPayoutTotal = nonPayoutTotal.plus(finding.txn().signedAmount());
                }
                case NEEDS_REVIEW -> {
                    reviewLines++;
                    reviewBank = reviewBank.plus(finding.txn().signedAmount());
                }
                default -> throw new IllegalStateException(
                        "Unexpected bank finding category: " + finding.category());
            }
        }

        Money bankCreditTotal = transactions.stream()
                .filter(CanonicalBankTxn::isCredit)
                .map(CanonicalBankTxn::credit)
                .reduce(Money.ZERO, Money::plus);
        Money bankDebitTotal = transactions.stream()
                .filter(CanonicalBankTxn::isDebit)
                .map(CanonicalBankTxn::debit)
                .reduce(Money.ZERO, Money::plus);

        Money bankAccounted = matchedBank.plus(reviewBank).plus(unmatchedDebitTotal).plus(nonPayoutTotal);
        Money bankResidual = bankCreditTotal.minus(bankDebitTotal).minus(bankAccounted);

        List<CategoryTotal> bankTotals = List.of(
                new CategoryTotal(MatchCategory.AUTO_MATCHED, matchedLines, matchedBank),
                new CategoryTotal(MatchCategory.NEEDS_REVIEW, reviewLines, reviewBank),
                new CategoryTotal(MatchCategory.UNMATCHED_BANK_DEBIT, unmatchedDebitCount, unmatchedDebitTotal),
                new CategoryTotal(MatchCategory.NON_PAYOUT, nonPayoutCount, nonPayoutTotal));

        boolean tieOut = payoutResidual.isZero() && bankResidual.isZero() && quality.count(Severity.ERROR) == 0;

        return new Summary(
                payouts.size(),
                registerTotal,
                excludedCount,
                excludedTotal,
                List.copyOf(payoutTotals),
                payoutCounts.getOrDefault(MatchCategory.AUTO_MATCHED, 0),
                matchedTotal,
                payoutCounts.getOrDefault(MatchCategory.NEEDS_REVIEW, 0),
                reviewTotal,
                payoutCounts.getOrDefault(MatchCategory.UNMATCHED_PAYOUT, 0),
                unmatchedTotal,
                transactions.size(),
                bankCreditTotal,
                bankDebitTotal,
                bankTotals,
                matchedLines,
                matchedBank,
                reviewLines,
                reviewBank,
                unmatchedDebitCount,
                unmatchedDebitTotal,
                nonPayoutCount,
                nonPayoutTotal,
                payoutResidual,
                bankResidual,
                tieOut,
                Map.copyOf(reviewReasons),
                quality.size(),
                (int) quality.count(Severity.ERROR),
                (int) quality.count(Severity.WARN));
    }
}
