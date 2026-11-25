package com.chronon.recon.service;

import com.chronon.recon.config.MatchingProperties;
import com.chronon.recon.domain.CanonicalBankTxn;
import com.chronon.recon.domain.CanonicalPayout;
import com.chronon.recon.domain.Summary;
import com.chronon.recon.exception.IngestionException;
import com.chronon.recon.ingest.IngestedTable;
import com.chronon.recon.ingest.WorkbookReader;
import com.chronon.recon.match.MatchOutcome;
import com.chronon.recon.match.Matcher;
import com.chronon.recon.ingest.mapping.MappingProfile;
import com.chronon.recon.ingest.mapping.MappingTarget;
import com.chronon.recon.ingest.mapping.ProfileRegistry;
import com.chronon.recon.ingest.parser.BankStatementParser;
import com.chronon.recon.ingest.parser.PayoutRegisterParser;
import com.chronon.recon.domain.quality.DataQualityLog;
import com.chronon.recon.report.ReportWriter;
import com.chronon.recon.report.SummaryCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

/**
 * Coordinates the reconciliation workflow:
 * Ingest -> Parse -> Match -> Tie-out -> Report.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private static final DateTimeFormatter RUN_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ProfileRegistry profiles;
    private final WorkbookReader reader;
    private final PayoutRegisterParser payoutParser;
    private final BankStatementParser bankParser;
    private final Matcher matcher;
    private final SummaryCalculator summaryCalculator;
    private final ReportWriter reportWriter;
    private final MatchingProperties matchingProperties;
    private final SecureRandom random = new SecureRandom();

    public ReconciliationService(ProfileRegistry profiles,
                                 WorkbookReader reader,
                                 PayoutRegisterParser payoutParser,
                                 BankStatementParser bankParser,
                                 Matcher matcher,
                                 SummaryCalculator summaryCalculator,
                                 ReportWriter reportWriter,
                                 MatchingProperties matchingProperties) {
        this.profiles = profiles;
        this.reader = reader;
        this.payoutParser = payoutParser;
        this.bankParser = bankParser;
        this.matcher = matcher;
        this.summaryCalculator = summaryCalculator;
        this.reportWriter = reportWriter;
        this.matchingProperties = matchingProperties;
    }

    public ReconciliationResult reconcile(byte[] payoutFile,
                                          String payoutFileName,
                                          byte[] bankFile,
                                          String bankFileName) {
        return reconcile(payoutFile, payoutFileName, bankFile, bankFileName, null, null);
    }

    public ReconciliationResult reconcile(byte[] payoutFile,
                                          String payoutFileName,
                                          byte[] bankFile,
                                          String bankFileName,
                                          String payoutProfileId,
                                          String bankProfileId) {
        DataQualityLog quality = new DataQualityLog();

        List<MappingProfile> payoutCandidates = (payoutProfileId != null && !payoutProfileId.isBlank())
                ? List.of(profiles.get(payoutProfileId))
                : profiles.forTarget(MappingTarget.PAYOUT_REGISTER);

        List<MappingProfile> bankCandidates = (bankProfileId != null && !bankProfileId.isBlank())
                ? List.of(profiles.get(bankProfileId))
                : profiles.forTarget(MappingTarget.BANK_STATEMENT);

        IngestedTable payoutInput = reader.readAuto(new ByteArrayInputStream(payoutFile), payoutCandidates, quality);
        IngestedTable bankInput = reader.readAuto(new ByteArrayInputStream(bankFile), bankCandidates, quality);

        MappingProfile payoutProfile = payoutInput.profile();
        MappingProfile bankProfile = bankInput.profile();

        List<CanonicalPayout> payouts = payoutParser.parse(
                payoutInput.table(), payoutInput.mapping(), payoutProfile, quality);
        List<CanonicalBankTxn> transactions = bankParser.parse(
                bankInput.table(), bankInput.mapping(), bankProfile, quality);

        if (payouts.isEmpty()) {
            throw new IngestionException("No usable payout rows were found in '" + payoutFileName
                    + "'. Check the profile '" + payoutProfile.id() + "' and the file contents.");
        }
        if (transactions.isEmpty()) {
            quality.error("Bank statement", 0, null,
                    "No transactions were found in '" + bankFileName + "'. Every payout will appear unmatched.");
        }

        MatchOutcome outcome = matcher.match(payouts, transactions, matchingProperties,
                bankProfile.nonPayoutKeywords());
        Summary summary = summaryCalculator.compute(payouts, transactions, outcome, quality);

        String runId = newRunId();
        ReconciliationResult result = new ReconciliationResult(
                runId,
                Instant.now(),
                payoutFileName,
                bankFileName,
                payoutProfile,
                bankProfile,
                payoutInput.mapping(),
                bankInput.mapping(),
                payouts,
                transactions,
                outcome,
                summary,
                quality,
                matchingProperties.describePolicy());

        log.info("Run {}: {} payouts ({} matched, {} review, {} unmatched), {} bank lines, tie-out {}",
                runId, summary.payoutCount(), summary.matchedCount(), summary.reviewCount(),
                summary.unmatchedPayoutCount(), summary.bankLineCount(),
                summary.tieOutOk() ? "PASSED" : "FAILED");
        return result;
    }

    public byte[] report(ReconciliationResult result) throws IOException {
        return reportWriter.write(result);
    }

    private String newRunId() {
        byte[] suffix = new byte[2];
        random.nextBytes(suffix);
        return "RUN-" + RUN_STAMP.format(java.time.LocalDateTime.now(ZoneId.systemDefault()))
                + "-" + HexFormat.of().withUpperCase().formatHex(suffix);
    }
}
