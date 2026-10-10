package com.sam.wwol;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComponentHealthTest {
    @Test
    void activeMeasurementsStayFreshFor24HoursWithoutUserActivity() {
        var health = new ComponentHealth(10_000, 30_000, 100, 2, 2);
        Instant start = Instant.parse("2026-10-10T00:00:00Z");
        for (int seconds = 0; seconds <= 86400; seconds += 10) {
            Instant attempt = start.plusSeconds(seconds), measured = attempt.plusMillis(5);
            health.record(true, 5, null, attempt, measured);
            var report = health.snapshot(measured.plusSeconds(9));
            assertEquals("operational", report.get("status"));
            assertEquals(attempt.toString(), report.get("lastAttempt"));
            assertEquals(measured.toString(), report.get("lastSuccess"));
            assertEquals(measured.toString(), report.get("lastMeasurement"));
        }
    }
    @Test
    void slowFailureRecoveryAndStalenessKeepDistinctTimestamps() {
        var health = new ComponentHealth(1000, 5000, 100, 2, 2);
        Instant time = Instant.parse("2026-10-10T00:00:00Z");
        health.record(true, 200, null, time, time.plusMillis(200));
        assertEquals("degraded", health.snapshot(time.plusMillis(200)).get("status"));
        assertNotNull(health.snapshot(time).get("lastSuccess"));
        health.record(false, 10, "Operation failed", time.plusSeconds(1), time.plusSeconds(1));
        health.record(false, 10, "Operation failed", time.plusSeconds(2), time.plusSeconds(2));
        assertEquals("down", health.snapshot(time.plusSeconds(2)).get("status"));
        health.record(true, 10, null, time.plusSeconds(3), time.plusSeconds(3));
        assertEquals("degraded", health.snapshot(time.plusSeconds(3)).get("status"));
        health.record(true, 10, null, time.plusSeconds(4), time.plusSeconds(4));
        var recovered = health.snapshot(time.plusSeconds(4));
        assertEquals("operational", recovered.get("status"));
        assertEquals(time.plusSeconds(2).toString(), recovered.get("lastFailure"));
        assertEquals(time.plusSeconds(4).toString(), recovered.get("lastStatusChange"));
        var stale = health.snapshot(time.plusSeconds(10));
        assertEquals("unknown", stale.get("status"));
        assertEquals(recovered.get("lastMeasurement"), stale.get("lastMeasurement"));
    }
    @Test
    void configuredFreshnessIncludesIntervalsAndTimeouts() {
        HealthSettings settings = new HealthSettings(Map.of("HEALTH_STATUS_MONITOR_STALE_MS", "1000"));
        var component = settings.component("STATUS_MONITOR", settings.statusInterval(), 30_000, settings.pingTimeout(), 100);
        Instant time = Instant.now();
        component.record(true, 1, null, time, time);
        assertEquals("operational", component.snapshot(time.plusSeconds(20)).get("status"));
    }
}
