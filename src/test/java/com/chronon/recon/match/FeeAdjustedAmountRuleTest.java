package com.chronon.recon.match;

import com.chronon.recon.config.MatchingProperties;
import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.CanonicalPayout;
import com.chronon.recon.domain.Confidence;
import com.chronon.recon.domain.Money;
import com.chronon.recon.domain.PayoutStatus;
import com.chronon.recon.match.rules.FeeAdjustedAmountRule;
import com.chronon.recon.ingest.normalizer.ReferenceExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FeeAdjustedAmountRuleTest {

    private final MatchingProperties properties = new MatchingProperties(
            1, 3, new BigDecimal("25.00"), new BigDecimal("0.50"), true, 3);
    private final MatchContext context = new MatchContext(properties);
    private final FeeAdjustedAmountRule rule = new FeeAdjustedAmountRule();
    private final LocalDate payoutDate = LocalDate.of(2025, 3, 4);

    @Test
    void shortCreditWithinToleranceGoesToReview() {
        MatchProposal proposal = evaluate("980.00", "NEFT CR XXXXXX4412", payoutDate);

        assertThat(proposal.confidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(proposal.bankAmount()).isEqualTo(Money.of("980.00"));
        assertThat(proposal.reason()).contains("20.00", "short", "fee tolerance");
    }

    @Test
    void creditExactlyAtToleranceBoundaryIsAccepted() {
        assertThat(evaluateOptional("975.00", "NEFT CR XXXXXX4412", payoutDate)).isPresent();
    }

    @Test
    void creditOutsideToleranceIsIgnored() {
        assertThat(evaluateOptional("974.99", "NEFT CR XXXXXX4412", payoutDate)).isEmpty();
    }

    @Test
    void creditNeedsCorroboration() {
        assertThat(evaluateOptional("980.00", "NEFT CR", payoutDate)).isEmpty();
    }

    @Test
    void creditOutsideDateWindowIsIgnored() {
        assertThat(evaluateOptional("980.00", "NEFT CR XXXXXX4412", payoutDate.plusDays(4))).isEmpty();
    }

    @Test
    void overpaymentIsReportedAsOver() {
        MatchProposal proposal = evaluate("1020.00", "NEFT CR XXXXXX4412", payoutDate);

        assertThat(proposal.reason()).contains("20.00", "over", "fee tolerance");
    }

    private MatchProposal evaluate(String amount, String narration, LocalDate bankDate) {
        return evaluateOptional(amount, narration, bankDate).orElseThrow();
    }

    private Optional<MatchProposal> evaluateOptional(String amount, String narration, LocalDate bankDate) {
        CanonicalPayout payout = new CanonicalPayout("PO-1", "EMP-1", "Rahul Sharma", "XXXXXX4412",
                "4412", "HDFC0000123", Money.of("1000.00"), payoutDate, List.of("CLM-1"),
                PayoutStatus.PAID, 2);
        CanonicalBankTxn txn = new CanonicalBankTxn(5, bankDate, bankDate, narration, "",
                null, Money.of(amount), null, ReferenceExtractor.tokens(narration));
        return rule.evaluate(payout, new CandidateIndex(List.of(txn)), context);
    }
}
