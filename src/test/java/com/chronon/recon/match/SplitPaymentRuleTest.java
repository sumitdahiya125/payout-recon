package com.chronon.recon.match;

import com.chronon.recon.config.MatchingProperties;
import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.CanonicalPayout;
import com.chronon.recon.domain.Confidence;
import com.chronon.recon.domain.Money;
import com.chronon.recon.domain.PayoutStatus;
import com.chronon.recon.match.rules.SplitPaymentRule;
import com.chronon.recon.ingest.normalizer.ReferenceExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SplitPaymentRuleTest {

    private static final BigDecimal FEE_AMOUNT = new BigDecimal("25.00");
    private static final BigDecimal FEE_PERCENT = new BigDecimal("0.50");

    private final SplitPaymentRule rule = new SplitPaymentRule();
    private final LocalDate payoutDate = LocalDate.of(2025, 3, 4);

    @Test
    void splitsAcrossTwoCorroboratedCredits() {
        MatchProposal proposal = evaluate(
                List.of(credit("NEFT CR XXXXXX4412", "600.00", 5),
                        credit("NEFT CR XXXXXX4412", "400.00", 6)),
                properties(true, 3)).orElseThrow();

        assertThat(proposal.confidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(proposal.txns()).hasSize(2);
        assertThat(proposal.bankAmount()).isEqualTo(Money.of("1000.00"));
        assertThat(proposal.reason()).contains("2 credits", "account, name or IFSC");
    }

    @Test
    void acceptsThreePartSplit() {
        MatchProposal proposal = evaluate(
                List.of(credit("NEFT CR XXXXXX4412", "500.00", 5),
                        credit("NEFT CR XXXXXX4412", "300.00", 6),
                        credit("NEFT CR XXXXXX4412", "200.00", 7)),
                properties(true, 3)).orElseThrow();

        assertThat(proposal.txns()).hasSize(3);
        assertThat(proposal.bankAmount()).isEqualTo(Money.of("1000.00"));
    }

    @Test
    void rejectsFourPartsWhenMaximumIsThree() {
        assertThat(evaluate(
                List.of(credit("NEFT CR XXXXXX4412", "400.00", 5),
                        credit("NEFT CR XXXXXX4412", "300.00", 6),
                        credit("NEFT CR XXXXXX4412", "200.00", 7),
                        credit("NEFT CR XXXXXX4412", "100.00", 8)),
                properties(true, 3))).isEmpty();
    }

    @Test
    void rejectsSplitWithoutCorroboration() {
        assertThat(evaluate(
                List.of(credit("NEFT CR", "600.00", 5),
                        credit("NEFT CR", "400.00", 6)),
                properties(true, 3))).isEmpty();
    }

    @Test
    void acceptsSplitWhenOnePartContainsPayoutReference() {
        MatchProposal proposal = evaluate(
                List.of(credit("NEFT CR-PO-1", "600.00", 5),
                        credit("NEFT CR", "400.00", 6)),
                properties(true, 3)).orElseThrow();

        assertThat(proposal.reason()).contains("payout reference");
    }

    @Test
    void rejectsSplitWhenDisabled() {
        assertThat(evaluate(
                List.of(credit("NEFT CR XXXXXX4412", "600.00", 5),
                        credit("NEFT CR XXXXXX4412", "400.00", 6)),
                properties(false, 3))).isEmpty();
    }

    @Test
    void rejectsWhenNoSubsetSumsToPayout() {
        assertThat(evaluate(
                List.of(credit("NEFT CR XXXXXX4412", "600.00", 5),
                        credit("NEFT CR XXXXXX4412", "300.00", 6)),
                properties(true, 3))).isEmpty();
    }

    private Optional<MatchProposal> evaluate(List<CanonicalBankTxn> txns, MatchingProperties properties) {
        CanonicalPayout payout = new CanonicalPayout("PO-1", "EMP-1", "Rahul Sharma", "XXXXXX4412",
                "4412", "HDFC0000123", Money.of("1000.00"), payoutDate, List.of("CLM-1"),
                PayoutStatus.PAID, 2);
        return rule.evaluate(payout, new CandidateIndex(new ArrayList<>(txns)), new MatchContext(properties));
    }

    private MatchingProperties properties(boolean allowSplit, int maxParts) {
        return new MatchingProperties(1, 3, FEE_AMOUNT, FEE_PERCENT, allowSplit, maxParts);
    }

    private CanonicalBankTxn credit(String narration, String amount, int row) {
        return new CanonicalBankTxn(row, payoutDate, payoutDate, narration, "",
                null, Money.of(amount), null, ReferenceExtractor.tokens(narration));
    }
}
