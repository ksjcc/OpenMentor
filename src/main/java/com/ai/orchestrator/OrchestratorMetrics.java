package com.ai.orchestrator;

import java.util.LinkedHashMap;
import java.util.Map;

public class OrchestratorMetrics {

    private final long startNanos = System.nanoTime();
    private final Map<String, Long> stageLatencyMs = new LinkedHashMap<>();

    public void recordStage(String stageName, long startedAtNanos) {
        stageLatencyMs.put(stageName, nanosToMillis(System.nanoTime() - startedAtNanos));
    }

    public Map<String, Long> snapshotStageLatencyMs() {
        return new LinkedHashMap<>(stageLatencyMs);
    }

    public long totalLatencyMs() {
        return nanosToMillis(System.nanoTime() - startNanos);
    }

    private long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }
}
