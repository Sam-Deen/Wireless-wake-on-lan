package com.sam.wwol;

import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Latest active measurement only, with no disk writes or user-traffic dependency. */
final class ComponentHealth {
    private final long interval, stale, slow;
    private final int failureThreshold, recoveryThreshold;
    private String status = "unknown", reason = "Not measured";
    private Instant lastAttempt, lastSuccess, lastFailure, lastMeasurement, lastStatusChange;
    private Double latency;
    private int failures, recoveries;
    ComponentHealth(long interval, long stale, long slow, int failureThreshold, int recoveryThreshold) {
        this.interval = interval; this.stale = stale; this.slow = slow;
        this.failureThreshold = failureThreshold; this.recoveryThreshold = recoveryThreshold;
    }
    synchronized void record(boolean ok, double latencyMs, String failureReason, Instant attempt, Instant measured) {
        lastAttempt = attempt; lastMeasurement = measured;
        latency = Math.min(86_400_000, Math.max(0, latencyMs));
        String next;
        if (!ok) {
            lastFailure = measured; failures = Math.min(1_000_000, failures + 1); recoveries = 0;
            next = failures >= failureThreshold ? "down" : "degraded"; reason = failureReason;
        } else {
            lastSuccess = measured; failures = 0;
            recoveries = latencyMs > slow ? 0 : Math.min(1_000_000, recoveries + 1);
            next = latencyMs > slow || (status.equals("down") || status.equals("degraded"))
                    && recoveries < recoveryThreshold ? "degraded" : "operational";
            reason = latencyMs > slow ? "Response latency elevated" : next.equals("degraded") ? "Recovering" : null;
        }
        if (!status.equals(next)) lastStatusChange = measured;
        status = next;
    }
    synchronized Map<String, Object> snapshot(Instant now) {
        long age = lastMeasurement == null ? 0 : Duration.between(lastMeasurement, now).toMillis();
        if (lastMeasurement != null && (age < -5000 || age > stale)) {
            if (!status.equals("unknown")) lastStatusChange = age < 0 ? now : lastMeasurement.plusMillis(stale);
            status = "unknown"; reason = "Monitoring result stale";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status); result.put("critical", true); result.put("reason", reason); result.put("latencyMs", latency);
        result.put("measurementType", "active"); result.put("intervalMs", interval);
        // Local freshness is enforced above. Allow for Docker/proxy transport delay.
        result.put("staleAfterMs", Math.max(180_000, stale));
        result.put("lastAttempt", text(lastAttempt)); result.put("lastSuccess", text(lastSuccess));
        result.put("lastFailure", text(lastFailure)); result.put("lastMeasurement", text(lastMeasurement));
        result.put("lastStatusChange", text(lastStatusChange));
        result.put("consecutiveFailures", failures); result.put("consecutiveRecoveries", recoveries);
        return result;
    }
    private static String text(Instant value) { return value == null ? null : value.toString(); }
}
