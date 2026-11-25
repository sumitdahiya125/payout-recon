package com.chronon.recon.exception;

/** Thrown when a requested reconciliation run ID is missing or has expired from memory. */
public class RunNotFoundException extends ReconciliationException {

    public RunNotFoundException(String runId) {
        super("Run " + runId + " is not available.",
              "Runs are kept in memory and the oldest are evicted. Re-run the reconciliation to get a new report.");
    }
}
