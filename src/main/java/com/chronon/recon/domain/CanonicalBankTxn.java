package com.chronon.recon.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * A single line from the bank statement, after normalisation.
 *
 * <p>Debit and credit are always stored as positive magnitudes; direction is expressed by which
 * field is populated. This mirrors how bank exports work and avoids sign-convention bugs.</p>
 *
 * @param tokens lower-cased alphanumeric tokens from the narration and reference column, used as
 *               the reference-matching index.
 */
public record CanonicalBankTxn(
        int sourceRow,
        LocalDate txnDate,
        LocalDate valueDate,
        String narration,
        String chequeRef,
        Money debit,
        Money credit,
        Money balance,
        List<String> tokens
) {

    public CanonicalBankTxn {
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
        debit = debit == null || debit.isZero() ? null : debit.abs();
        credit = credit == null || credit.isZero() ? null : credit.abs();
    }

    public boolean isCredit() {
        return credit != null;
    }

    public boolean isDebit() {
        return debit != null;
    }

    public Money amount() {
        return credit != null ? credit : (debit != null ? debit : Money.ZERO);
    }

    public Money signedAmount() {
        if (credit != null) {
            return credit;
        }
        return debit != null ? debit.negate() : Money.ZERO;
    }

    public LocalDate effectiveDate() {
        return txnDate != null ? txnDate : valueDate;
    }

    public String description() {
        String text = narration == null ? "" : narration.trim();
        if (text.isEmpty() && chequeRef != null) {
            text = chequeRef.trim();
        }
        return text;
    }

    /** Folded text used for keyword checks. */
    public String foldedText() {
        return (narration == null ? "" : narration).toLowerCase(Locale.ROOT)
                + " " + (chequeRef == null ? "" : chequeRef).toLowerCase(Locale.ROOT);
    }

    public boolean hasToken(String token) {
        return token != null && !token.isBlank() && tokens.contains(token.toLowerCase(Locale.ROOT));
    }

    public String shortDescription() {
        String text = description();
        return text.length() <= 70 ? text : text.substring(0, 67) + "...";
    }
}
