package com.chronon.recon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Report and run-retention settings. */
@ConfigurationProperties(prefix = "recon.report")
public record ReportProperties(Integer maxRunsRetained) {

    public ReportProperties {
        maxRunsRetained = maxRunsRetained == null ? 10 : maxRunsRetained;
    }
}
