package com.chronon.recon.match;

import com.chronon.recon.config.MatchingProperties;
import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.CanonicalPayout;
import com.chronon.recon.domain.Confidence;
import com.chronon.recon.domain.MatchCategory;
import com.chronon.recon.domain.Money;
import com.chronon.recon.domain.PayoutMatch;
import com.chronon.recon.domain.PayoutStatus;
import com.chronon.recon.ingest.mapping.MappingProfile;
import com.chronon.recon.match.rules.AmountAccountDateRule;
import com.chronon.recon.match.rules.ExactReferenceRule;
import com.chronon.recon.match.rules.FeeAdjustedAmountRule;
import com.chronon.recon.match.rules.SplitPaymentRule;
import com.chronon.recon.ingest.normalizer.ReferenceExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatcherConflictTest {

    private final MatchingProperties properties = new MatchingProperties(
            1, 3, new BigDecimal("25.00"), new BigDecimal("0.50"), true, 3);
    private final Matcher matcher = new Matcher(
            List.of(new ExactReferenceRule(), new AmountAccountDateRule(),
                    new FeeAdjustedAmountRule(), new SplitPaymentRule()),
            new BankLineClassifier());
    private final LocalDate date = LocalDate.of(2025, 3, 4);

    @Test
    void secondPayoutClaimingTheSameCreditGoesToReview() {
        CanonicalPayout firstPayout = payout("PO-1", 2);
        CanonicalPayout secondPayout = payout("PO-2", 3);
        CanonicalBankTxn credit = credit("NEFT CR XXXXXX4412", "1000.00", 5);

        MatchOutcome outcome = matcher.match(
                List.of(firstPayout, secondPayout), List.of(credit), properties,
                MappingProfile.DEFAULT_NON_PAYOUT_KEYWORDS);

        PayoutMatch first = outcome.payoutMatches().get(0);
        PayoutMatch second = outcome.payoutMatches().get(1);

        assertThat(first.payout().payoutId()).isEqualTo("PO-1");
        assertThat(first.category()).isEqualTo(MatchCategory.AUTO_MATCHED);
        assertThat(first.confidence()).isEqualTo(Confidence.HIGH);

        assertThat(second.payout().payoutId()).isEqualTo("PO-2");
        assertThat(second.category()).isEqualTo(MatchCategory.NEEDS_REVIEW);
        assertThat(second.confidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(second.reason()).contains("already used by payout PO-1");

        assertThat(outcome.bankFindings()).isEmpty();
    }

    private CanonicalPayout payout(String payoutId, int sourceRow) {
        return new CanonicalPayout(payoutId, "EMP-" + payoutId, "Rahul Sharma", "XXXXXX4412",
                "4412", "HDFC0000123", Money.of("1000.00"), date, List.of("CLM-" + payoutId),
                PayoutStatus.PAID, sourceRow);
    }

    private CanonicalBankTxn credit(String narration, String amount, int sourceRow) {
        return new CanonicalBankTxn(sourceRow, date, date, narration, "",
                null, Money.of(amount), null, ReferenceExtractor.tokens(narration));
    }
}
