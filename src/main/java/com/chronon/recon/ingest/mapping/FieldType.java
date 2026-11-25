package com.chronon.recon.ingest.mapping;

/** How a mapped column's raw cell value should be interpreted. */
public enum FieldType {
    STRING,
    MONEY,
    DATE,
    ACCOUNT,
    STATUS,
    REFERENCE,
    DRCR,
    ID_LIST
}
