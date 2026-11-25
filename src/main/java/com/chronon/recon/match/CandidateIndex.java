package com.chronon.recon.match;

import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.Money;
import com.chronon.recon.ingest.normalizer.ReferenceExtractor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Indexes the bank statement so matching is linear rather than a nested scan of two files.
 *
 * <p>Reference lookup is token-based: narrations are tokenised once and indexed, then a payout
 * reference is looked up by its folded form, its digits and its last six digits, which tolerates
 * the truncation banks routinely apply.</p>
 */
public class CandidateIndex {

    private final List<CanonicalBankTxn> transactions;
    private final Map<String, List<CanonicalBankTxn>> byToken = new LinkedHashMap<>();
    private final Map<String, List<CanonicalBankTxn>> byCreditAmount = new LinkedHashMap<>();
    private final List<CanonicalBankTxn> credits = new ArrayList<>();
    private final List<CanonicalBankTxn> debits = new ArrayList<>();

    public CandidateIndex(List<CanonicalBankTxn> transactions) {
        this.transactions = List.copyOf(transactions);
        for (CanonicalBankTxn txn : this.transactions) {
            for (String token : txn.tokens()) {
                byToken.computeIfAbsent(token, key -> new ArrayList<>()).add(txn);
            }
            if (txn.isCredit()) {
                credits.add(txn);
                byCreditAmount.computeIfAbsent(key(txn.credit()), key -> new ArrayList<>()).add(txn);
            }
            if (txn.isDebit()) {
                debits.add(txn);
            }
        }
    }

    private static String key(Money money) {
        return money.amount().toPlainString();
    }

    public List<CanonicalBankTxn> all() {
        return transactions;
    }

    public List<CanonicalBankTxn> credits() {
        return List.copyOf(credits);
    }

    public List<CanonicalBankTxn> debits() {
        return List.copyOf(debits);
    }

    public List<CanonicalBankTxn> byReference(String reference) {
        Map<Integer, CanonicalBankTxn> hits = new LinkedHashMap<>();
        for (String lookup : ReferenceExtractor.lookupKeys(reference)) {
            for (CanonicalBankTxn txn : byToken.getOrDefault(lookup, List.of())) {
                hits.putIfAbsent(txn.sourceRow(), txn);
            }
        }
        return List.copyOf(hits.values());
    }

    public List<CanonicalBankTxn> creditsExactly(Money amount) {
        return List.copyOf(byCreditAmount.getOrDefault(key(amount), List.of()));
    }

    public List<CanonicalBankTxn> creditsWithin(Money amount, Money tolerance) {
        List<CanonicalBankTxn> matches = new ArrayList<>();
        for (CanonicalBankTxn txn : credits) {
            if (txn.credit().minus(amount).abs().compareTo(tolerance) <= 0) {
                matches.add(txn);
            }
        }
        return matches;
    }

    public List<CanonicalBankTxn> creditsInWindow(LocalDate from, LocalDate to) {
        List<CanonicalBankTxn> matches = new ArrayList<>();
        for (CanonicalBankTxn txn : credits) {
            LocalDate date = txn.effectiveDate();
            if (date != null && !date.isBefore(from) && !date.isAfter(to)) {
                matches.add(txn);
            }
        }
        return matches;
    }

    public List<CanonicalBankTxn> debitsReferencing(String reference) {
        List<CanonicalBankTxn> matches = new ArrayList<>();
        for (CanonicalBankTxn txn : debits) {
            if (ReferenceExtractor.matches(reference, txn.tokens())) {
                matches.add(txn);
            }
        }
        return matches;
    }
}
