package com.chronon.recon.match;

import com.chronon.recon.config.MatchingProperties;
import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.CanonicalPayout;
import com.chronon.recon.domain.Money;
import com.chronon.recon.domain.PayoutStatus;
import com.chronon.recon.ingest.normalizer.ReferenceExtractor;
import com.chronon.recon.match.rules.AmountAccountDateRule;
import com.chronon.recon.match.rules.ExactReferenceRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MatchRuleTest {

    private final MatchingProperties properties = new MatchingProperties(1, 3, null, null, true, 3);
    private final MatchContext context = new MatchContext(properties);

    private CanonicalPayout payout(Money amount, LocalDate date) {
        return new CanonicalPayout("PO-1", "EMP-1", "Rahul Sharma", "XXXXXX4412", "4412",
                "HDFC0000123", amount, date, List.of("CLM-1"), PayoutStatus.PAID, 2);
    }

    private CanonicalBankTxn credit(String narration, Money amount, LocalDate date, int row) {
        return new CanonicalBankTxn(row, date, date, narration, "",
                null, amount, null, ReferenceExtractor.tokens(narration));
    }

    @Test
    void referenceMatchBooksAtHighConfidence() {
        LocalDate date = LocalDate.of(2025, 3, 4);
        CanonicalBankTxn txn = credit("NEFT CR-PO-1-RAHUL SHARMA", Money.of("1000.00"), date, 5);
        CandidateIndex index = new CandidateIndex(List.of(txn));

        Optional<MatchProposal> proposal = new ExactReferenceRule().evaluate(payout(Money.of("1000.00"), date), index, context);

        assertThat(proposal).isPresent();
        assertThat(proposal.get().confidence().isHigh()).isTrue();
        assertThat(proposal.get().reason()).contains("PO-1");
    }

    @Test
    void exactAmountWithAccountBooksAtHighConfidence() {
        LocalDate date = LocalDate.of(2025, 3, 4);
        CanonicalBankTxn txn = credit("NEFT CR XXXXXX4412", Money.of("1000.00"), date, 5);
        CandidateIndex index = new CandidateIndex(List.of(txn));

        Optional<MatchProposal> proposal = new AmountAccountDateRule().evaluate(payout(Money.of("1000.00"), date), index, context);

        assertThat(proposal).isPresent();
        assertThat(proposal.get().confidence().isHigh()).isTrue();
    }

    @Test
    void exactAmountWithoutCorroborationIsOnlyMedium() {
        LocalDate date = LocalDate.of(2025, 3, 4);
        CanonicalBankTxn txn = credit("NEFT CR", Money.of("1000.00"), date, 5);
        CandidateIndex index = new CandidateIndex(List.of(txn));

        Optional<MatchProposal> proposal = new AmountAccountDateRule().evaluate(payout(Money.of("1000.00"), date), index, context);

        assertThat(proposal).isPresent();
        assertThat(proposal.get().confidence().isHigh()).isFalse();
    }

    @Test
    void noCandidate() {
        LocalDate date = LocalDate.of(2025, 3, 4);
        CanonicalBankTxn txn = credit("SALARY", Money.of("50000.00"), date, 5);
        CandidateIndex index = new CandidateIndex(List.of(txn));

        assertThat(new ExactReferenceRule().evaluate(payout(Money.of("1000.00"), date), index, context)).isEmpty();
    }
}
