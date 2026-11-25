package com.chronon.recon.domain;

import java.util.Locale;

/** Normalised payout status. Unknown values are preserved as UNKNOWN rather than dropped. */
public enum PayoutStatus {
    PAID,
    PENDING,
    PROCESSING,
    FAILED,
    CANCELLED,
    REVERSED,
    UNKNOWN;

    public static PayoutStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        return switch (value) {
            case "PAID", "SUCCESS", "SUCCESSFUL", "SETTLED", "COMPLETED", "DONE", "DISBURSED" -> PAID;
            case "PENDING", "INITIATED", "QUEUED", "SCHEDULED" -> PENDING;
            case "PROCESSING", "INPROGRESS", "SENTTOBANK" -> PROCESSING;
            case "FAILED", "FAILURE", "ERROR", "REJECTED", "BOUNCED" -> FAILED;
            case "CANCELLED", "CANCELED", "VOID" -> CANCELLED;
            case "REVERSED", "RETURNED", "REFUNDED" -> REVERSED;
            default -> UNKNOWN;
        };
    }

    /**
     * A payout is payable when finance still expects money to have left the bank for it.
     * Failed and cancelled payouts are deliberately excluded from matching.
     */
    public boolean isPayable() {
        return this != FAILED && this != CANCELLED;
    }
}
