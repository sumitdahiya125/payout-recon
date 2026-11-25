package com.chronon.recon.service;

import com.chronon.recon.config.ReportProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Holds finished runs in memory so the browser can download the report.
 *
 * <p>Bounded on purpose: a take-home tool should not leak memory by accumulating every uploaded
 * file forever. The oldest run is evicted when the configured limit is reached.</p>
 */
@Component
public class RunStore {

    public record StoredRun(ReconciliationResult result, byte[] report, Instant createdAt) {
    }

    private final Map<String, StoredRun> runs;
    private final int limit;

    public RunStore(ReportProperties properties) {
        this.limit = Math.max(1, properties.maxRunsRetained());
        this.runs = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, StoredRun> eldest) {
                return size() > RunStore.this.limit;
            }
        };
    }

    public synchronized void put(StoredRun run) {
        runs.put(run.result().runId(), run);
    }

    public synchronized Optional<StoredRun> get(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    public synchronized int size() {
        return runs.size();
    }
}
