package com.sam.wwol;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.TimeUnit;

@Service
public class StatusService {

    private final String targetIp;
    private final HealthSettings settings;
    private final ComponentHealth health;

    private volatile Measurement measurement;

    public record Measurement(String targetStatus, Instant lastAttempt, Instant lastSuccess,
                              double latencyMs, int consecutiveFailures, String reason) {}

    private record Probe(boolean completed, boolean online, String reason) {}

    @Autowired
    public StatusService(@Value("${target.ip}") String targetIp, HealthSettings settings) {
        this.targetIp = targetIp;
        this.settings = settings;
        health = settings.component("STATUS_MONITOR", settings.statusInterval(), 30_000, settings.pingTimeout(), 1500);
    }
    public StatusService(String targetIp) { this(targetIp, new HealthSettings(Map.of())); }

    public boolean ping() {
        return probe().online();
    }

    private Probe probe() {
        Process process = null;
        try {
            process = startPing();
            if (!process.waitFor(settings.pingTimeout(), TimeUnit.MILLISECONDS)) {
                return new Probe(false, false, "Request timed out");
            }
            int code = process.exitValue();
            // Linux ping uses 1 for an offline host; this is a successful
            // status measurement, not a failed Wake-on-LAN service.
            return code == 0 || code == 1
                    ? new Probe(true, code == 0, null)
                    : new Probe(false, false, "Operation failed");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new Probe(false, false, "Operation failed");
        } catch (java.io.IOException | RuntimeException error) {
            return new Probe(false, false, "Operation failed");
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    Process startPing() throws java.io.IOException {
        return new ProcessBuilder("ping", "-c", "1", "-W", "1", targetIp)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    public void updateStatus() {
        Instant attempt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        long started = System.nanoTime();
        Probe result = probe();
        Instant measured = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        health.record(result.completed(), (System.nanoTime() - started) / 1_000_000.0, result.reason(), attempt, measured);
        Measurement previous = measurement;
        measurement = new Measurement(result.completed() ? result.online() ? "on" : "off" : "unknown",
                attempt, result.completed() ? measured : previous == null ? null : previous.lastSuccess(),
                (System.nanoTime() - started) / 1_000_000.0,
                result.completed() ? 0 : previous == null ? 1 : Math.min(1_000_000, previous.consecutiveFailures() + 1),
                result.reason());
    }

    public String getStatus() {
        Measurement latest = measurement;
        return latest == null ? "unknown" : latest.targetStatus();
    }

    public Measurement getMeasurement() { return measurement; }
    public Map<String, Object> getHealth(Instant now) { return health.snapshot(now); }
}
