package com.chronon.recon.domain;

/** How sure the tool is about a particular match. Only HIGH is booked without human review. */
public enum Confidence {
    HIGH(3, "High"),
    MEDIUM(2, "Medium"),
    LOW(1, "Low"),
    NONE(0, "None");

    private final int rank;
    private final String label;

    Confidence(int rank, String label) {
        this.rank = rank;
        this.label = label;
    }

    public int rank() {
        return rank;
    }

    public String label() {
        return label;
    }

    public boolean isHigh() {
        return this == HIGH;
    }
}
