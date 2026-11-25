package com.chronon.recon.match;

import com.chronon.recon.config.MatchingProperties;
import com.chronon.recon.domain.BankFinding;
import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.CanonicalPayout;
import com.chronon.recon.domain.MatchCategory;
import com.chronon.recon.domain.Money;
import com.chronon.recon.domain.PayoutMatch;
import com.chronon.recon.domain.PayoutStatus;
import com.chronon.recon.ingest.normalizer.ReferenceExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BankLineClassifierTest {

    private final BankLineClassifier classifier = new BankLineClassifier();
    private final MatchingProperties properties = new MatchingProperties(
            1, 3, new BigDecimal("25.00"), new BigDecimal("0.50"), true, 3);
    private final List<String> keywords = List.of("salary", "charges");
    private final LocalDate date = LocalDate.of(2025, 3, 4);

    @Test
    void debitReferencingPayoutGoesToReview() {
        BankFinding finding = classifier.classify(
                debit("RTN PO-1", "1000.00"), List.of(), List.of(payout("PO-1")), properties, keywords);

        assertThat(finding.category()).isEqualTo(MatchCategory.NEEDS_REVIEW);
        assertThat(finding.reason()).contains("return or reversal");
    }

    @Test
    void unrelatedDebitWithKeywordIsNonPayout() {
        BankFinding finding = classifier.classify(
                debit("SALARY MARCH", "50000.00"), List.of(), List.of(), properties, keywords);

        assertThat(finding.category()).isEqualTo(MatchCategory.NON_PAYOUT);
        assertThat(finding.reason()).contains("salary");
    }

    @Test
    void unrelatedDebitWithoutKeywordIsUnmatched() {
        BankFinding finding = classifier.classify(
                debit("NEFT OUT", "4500.00"), List.of(), List.of(), properties, keywords);

        assertThat(finding.category()).isEqualTo(MatchCategory.UNMATCHED_BANK_DEBIT);
    }

    @Test
    void creditWithKeywordIsNonPayout() {
        BankFinding finding = classifier.classify(
                credit("SALARY CREDIT", "50000.00"), List.of(), List.of(), properties, keywords);

        assertThat(finding.category()).isEqualTo(MatchCategory.NON_PAYOUT);
        assertThat(finding.reason()).contains("salary");
    }

    @Test
    void creditMatchingUnmatchedPayoutAmountGoesToReview() {
        CanonicalPayout payout = payout("PO-1");
        PayoutMatch unmatched = PayoutMatch.unmatched(payout, "no-candidate", "No matching credit", "No match");

        BankFinding finding = classifier.classify(
                credit("NEFT CR", "1000.00"), List.of(unmatched), List.of(payout), properties, keywords);

        assertThat(finding.category()).isEqualTo(MatchCategory.NEEDS_REVIEW);
        assertThat(finding.reason()).contains("matches the amount of unmatched payout PO-1");
    }

    @Test
    void unrelatedCreditIsNonPayout() {
        BankFinding finding = classifier.classify(
                credit("NEFT CR", "1234.00"), List.of(), List.of(payout("PO-1")), properties, keywords);

        assertThat(finding.category()).isEqualTo(MatchCategory.NON_PAYOUT);
        assertThat(finding.reason()).contains("No payout reference");
    }

    private CanonicalPayout payout(String payoutId) {
        return new CanonicalPayout(payoutId, "EMP-1", "Rahul Sharma", "XXXXXX4412", "4412",
                "HDFC0000123", Money.of("1000.00"), date, List.of("CLM-1"), PayoutStatus.PAID, 2);
    }

    private CanonicalBankTxn debit(String narration, String amount) {
        return new CanonicalBankTxn(5, date, date, narration, "",
                Money.of(amount), null, null, ReferenceExtractor.tokens(narration));
    }

    private CanonicalBankTxn credit(String narration, String amount) {
        return new CanonicalBankTxn(5, date, date, narration, "",
                null, Money.of(amount), null, ReferenceExtractor.tokens(narration));
    }
}
