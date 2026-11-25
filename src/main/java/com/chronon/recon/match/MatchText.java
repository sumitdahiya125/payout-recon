package com.chronon.recon.match;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Human phrasing shared by the rules, so every explanation reads the same way. */
public final class MatchText {

    private MatchText() {
    }

    public static String relativeDate(LocalDate payoutDate, LocalDate bankDate) {
        if (payoutDate == null || bankDate == null) {
            return "on an unknown date";
        }
        long days = ChronoUnit.DAYS.between(payoutDate, bankDate);
        if (days == 0) {
            return "on the same day as the payout";
        }
        long absolute = Math.abs(days);
        String unit = absolute == 1 ? " day " : " days ";
        return days > 0
                ? absolute + unit + "after the payout date"
                : absolute + unit + "before the payout date";
    }

    public static String amounts(String label, java.util.List<com.chronon.recon.domain.CanonicalBankTxn> txns) {
        StringBuilder text = new StringBuilder();
        for (com.chronon.recon.domain.CanonicalBankTxn txn : txns) {
            if (!text.isEmpty()) {
                text.append(" + ");
            }
            text.append(txn.amount().display());
        }
        return label + " " + text;
    }
}
