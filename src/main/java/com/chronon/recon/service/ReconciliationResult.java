package com.chronon.recon.service;

import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.CanonicalPayout;
import com.chronon.recon.domain.Summary;
import com.chronon.recon.match.MatchOutcome;
import com.chronon.recon.ingest.mapping.ColumnMapping;
import com.chronon.recon.ingest.mapping.MappingProfile;
import com.chronon.recon.domain.quality.DataQualityLog;

import java.time.Instant;
import java.util.List;

/**
 * Everything a report needs, captured at the moment the run finished so the workbook can be
 * regenerated identically later (the mapping and policy snapshot are embedded in the report).
 */
public record ReconciliationResult(
        String runId,
        Instant generatedAt,
        String payoutFileName,
        String bankFileName,
        MappingProfile payoutProfile,
        MappingProfile bankProfile,
        ColumnMapping payoutMapping,
        ColumnMapping bankMapping,
        List<CanonicalPayout> payouts,
        List<CanonicalBankTxn> transactions,
        MatchOutcome outcome,
        Summary summary,
        DataQualityLog quality,
        String matchingPolicy
) {
}
