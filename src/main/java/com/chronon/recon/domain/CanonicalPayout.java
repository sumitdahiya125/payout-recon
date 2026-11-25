package com.chronon.recon.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * A payout as it appears in the Chronon payout register, after normalisation.
 *
 * @param sourceRow 1-based spreadsheet row this record came from, so finance can trace it back.
 */
public record CanonicalPayout(
        String payoutId,
        String employeeId,
        String employeeName,
        String accountMasked,
        String accountTail,
        String ifsc,
        Money amount,
        LocalDate payoutDate,
        List<String> claimIds,
        PayoutStatus status,
        int sourceRow
) {

    public CanonicalPayout {
        claimIds = claimIds == null ? List.of() : List.copyOf(claimIds);
        accountTail = (accountTail == null || accountTail.isBlank())
                ? AccountNumbers.tail(accountMasked)
                : accountTail;
        ifsc = AccountNumbers.normalizeIfsc(ifsc);
    }

    public boolean isPayable() {
        return status.isPayable();
    }

    /** All identifiers finance might find in a bank narration, strongest first. */
    public List<String> referenceIds() {
        List<String> ids = new java.util.ArrayList<>();
        if (payoutId != null && !payoutId.isBlank()) {
            ids.add(payoutId.trim());
        }
        claimIds.stream().filter(c -> c != null && !c.isBlank()).forEach(ids::add);
        return ids;
    }

    public String displayName() {
        return employeeName == null || employeeName.isBlank() ? "(unknown employee)" : employeeName;
    }

    public String describe() {
        return payoutId + " (" + displayName() + ", " + amount.display() + ", " + payoutDate + ")";
    }
}
