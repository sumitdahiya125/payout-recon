package com.chronon.recon.match;

import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.CanonicalPayout;
import com.chronon.recon.ingest.normalizer.TextNormalizer;

import java.util.List;
import java.util.Locale;

/**
 * Independent evidence that a bank credit belongs to a payout. The matcher never treats a credit
 * as confirmed on amount and date alone; it needs one of these as well.
 */
public final class Corroboration {

    private Corroboration() {
    }

    public static boolean accountTail(CanonicalBankTxn txn, CanonicalPayout payout) {
        String tail = payout.accountTail();
        return tail != null && tail.length() >= 4
                && txn.tokens().contains(tail.toLowerCase(Locale.ROOT));
    }

    public static boolean employeeName(CanonicalBankTxn txn, CanonicalPayout payout) {
        List<String> words = TextNormalizer.words(payout.employeeName()).stream()
                .filter(word -> word.length() >= 4)
                .toList();
        if (words.isEmpty()) {
            return false;
        }
        String narration = TextNormalizer.fold(txn.description());
        long matched = words.stream().filter(narration::contains).count();
        if (words.size() == 1) {
            return matched >= 1;
        }
        // Multi-word names: require both parts, unless a single distinctive long token appears.
        if (matched >= 2) {
            return true;
        }
        return matched == 1 && words.stream().anyMatch(word -> word.length() >= 7 && narration.contains(word));
    }

    public static boolean ifsc(CanonicalBankTxn txn, CanonicalPayout payout) {
        String ifsc = payout.ifsc();
        if (ifsc == null || ifsc.length() < 8) {
            return false;
        }
        return TextNormalizer.fold(txn.description()).contains(TextNormalizer.fold(ifsc));
    }

    public static boolean any(CanonicalBankTxn txn, CanonicalPayout payout) {
        return accountTail(txn, payout) || employeeName(txn, payout) || ifsc(txn, payout);
    }

    public static String describe(CanonicalBankTxn txn, CanonicalPayout payout) {
        if (accountTail(txn, payout)) {
            return "the account ending " + payout.accountTail();
        }
        if (employeeName(txn, payout)) {
            return "the beneficiary name '" + payout.displayName() + "'";
        }
        if (ifsc(txn, payout)) {
            return "the IFSC " + payout.ifsc();
        }
        return "no account, name or IFSC corroboration";
    }
}
