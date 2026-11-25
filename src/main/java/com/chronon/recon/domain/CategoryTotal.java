package com.chronon.recon.domain;

/** Count and money total for one category, used in the summary sheet. */
public record CategoryTotal(MatchCategory category, int count, Money amount) {
}
