package com.chronon.recon.ingest;

import com.chronon.recon.ingest.mapping.ColumnMapping;
import com.chronon.recon.ingest.mapping.MappingProfile;

/** A parsed sheet together with the column mapping and profile that was used, for the report's audit trail. */
public record IngestedTable(RawTable table, ColumnMapping mapping, MappingProfile profile) {
    public IngestedTable(RawTable table, ColumnMapping mapping) {
        this(table, mapping, null);
    }
}
