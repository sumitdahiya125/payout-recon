package com.chronon.recon.report;

import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.CanonicalPayout;
import com.chronon.recon.domain.Confidence;
import com.chronon.recon.domain.MatchCategory;
import com.chronon.recon.domain.Money;
import com.chronon.recon.domain.PayoutMatch;
import com.chronon.recon.domain.PayoutStatus;
import com.chronon.recon.domain.Summary;
import com.chronon.recon.match.MatchOutcome;
import com.chronon.recon.ingest.normalizer.ReferenceExtractor;
import com.chronon.recon.domain.quality.DataQualityLog;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryCalculatorTest {

    private final SummaryCalculator calculator = new SummaryCalculator();
    private final LocalDate date = LocalDate.of(2025, 3, 4);

    @Test
    void tieOutPassesWhenResidualsZeroAndZeroErrors() {
        CanonicalPayout payout = payout("PO-1", "1000.00", 2);
        CanonicalBankTxn txn = credit("1000.00", 5);

        PayoutMatch match = new PayoutMatch(payout, List.of(txn), MatchCategory.AUTO_MATCHED,
                Confidence.HIGH, "ref", "Reference", "Matched", Money.of("1000.00"));

        DataQualityLog quality = new DataQualityLog();
        quality.info("Payout register", 2, "field", "Normal info");

        Summary summary = calculator.compute(List.of(payout), List.of(txn),
                new MatchOutcome(List.of(match), List.of()), quality);

        assertThat(summary.payoutResidual().isZero()).isTrue();
        assertThat(summary.bankResidual().isZero()).isTrue();
        assertThat(summary.errorCount()).isZero();
        assertThat(summary.tieOutOk()).isTrue();
    }

    @Test
    void tieOutFailsWhenErrorLevelDataQualityIssueOccursEvenIfResidualsZero() {
        CanonicalPayout payout = payout("PO-1", "1000.00", 2);
        CanonicalBankTxn txn = credit("1000.00", 5);

        PayoutMatch match = new PayoutMatch(payout, List.of(txn), MatchCategory.AUTO_MATCHED,
                Confidence.HIGH, "ref", "Reference", "Matched", Money.of("1000.00"));

        DataQualityLog quality = new DataQualityLog();
        quality.error("Bank statement", 10, "credit", "Corrupted or unreadable row");

        Summary summary = calculator.compute(List.of(payout), List.of(txn),
                new MatchOutcome(List.of(match), List.of()), quality);

        assertThat(summary.payoutResidual().isZero()).isTrue();
        assertThat(summary.bankResidual().isZero()).isTrue();
        assertThat(summary.errorCount()).isEqualTo(1);
        // Financial invariant: tie-out MUST fail if fatal data quality errors occurred
        assertThat(summary.tieOutOk()).isFalse();
    }

    @Test
    void tieOutFailsWhenResidualIsNonZero() {
        CanonicalPayout payout1 = payout("PO-1", "1000.00", 2);
        CanonicalPayout payout2 = payout("PO-2", "500.00", 3);
        CanonicalBankTxn txn = credit("1000.00", 5);

        // payout2 is not accounted for in outcome, so payoutResidual = 500.00 != 0
        PayoutMatch match = new PayoutMatch(payout1, List.of(txn), MatchCategory.AUTO_MATCHED,
                Confidence.HIGH, "ref", "Reference", "Matched", Money.of("1000.00"));

        DataQualityLog quality = new DataQualityLog();

        Summary summary = calculator.compute(List.of(payout1, payout2), List.of(txn),
                new MatchOutcome(List.of(match), List.of()), quality);

        assertThat(summary.payoutResidual().isZero()).isFalse();
        assertThat(summary.tieOutOk()).isFalse();
    }

    private CanonicalPayout payout(String id, String amount, int row) {
        return new CanonicalPayout(id, "EMP-1", "Rahul", "XXXXXX1234", "1234",
                "HDFC0000123", Money.of(amount), date, List.of("CLM-1"), PayoutStatus.PAID, row);
    }

    private CanonicalBankTxn credit(String amount, int row) {
        return new CanonicalBankTxn(row, date, date, "NEFT CR", "",
                null, Money.of(amount), null, ReferenceExtractor.tokens("NEFT CR"));
    }
}
