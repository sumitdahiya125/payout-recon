package com.chronon.recon.ingest.normalizer;

/** Which component comes first in an ambiguous numeric date such as 04/03/2025. */
public enum DateOrder {
    DAY_FIRST,
    MONTH_FIRST
}
