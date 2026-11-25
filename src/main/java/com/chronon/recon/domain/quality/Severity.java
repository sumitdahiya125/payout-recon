package com.chronon.recon.domain.quality;

/** Severity of a data-quality finding. ERROR rows are not reconciled; WARN rows are reconciled but suspicious. */
public enum Severity {
    INFO,
    WARN,
    ERROR
}
