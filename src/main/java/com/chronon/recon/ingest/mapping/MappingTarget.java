package com.chronon.recon.ingest.mapping;

/** Which of the two inputs a profile describes. */
public enum MappingTarget {
    PAYOUT_REGISTER("Payout register"),
    BANK_STATEMENT("Bank statement");

    private final String label;

    MappingTarget(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
