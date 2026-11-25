package com.chronon.recon.match;

import com.chronon.recon.config.MatchingProperties;

/** Everything a rule needs beyond the payout and the bank index. */
public record MatchContext(MatchingProperties properties) {
}
