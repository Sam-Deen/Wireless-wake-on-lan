package com.sam.wwol;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ApplicationHealthMonitor {
    private final StatusService status;
    private final PowerService power;
    private final ComponentHealth application, wake;
    private final Clock clock;
    @Autowired
    public ApplicationHealthMonitor(StatusService status, PowerService power, HealthSettings settings) {
        this(status, power, settings, Clock.systemUTC());
    }
    ApplicationHealthMonitor(StatusService status, PowerService power, HealthSettings settings, Clock clock) {
        this.status = status; this.power = power; this.clock = clock;
        application = settings.component("APPLICATION", settings.applicationInterval(), 90_000, settings.pingTimeout(), 250);
        wake = settings.component("WAKE_TRANSPORT", settings.wakeInterval(), 90_000, settings.pingTimeout(), 100);
    }
    @Scheduled(fixedDelayString = "#{@healthSettings.applicationInterval()}")
    public void checkApplication() { measure(application, () -> { status.getStatus(); return true; }); }
    @Scheduled(fixedDelayString = "#{@healthSettings.wakeInterval()}")
    public void checkWake() { measure(wake, power::wakeTransportAvailable); }
    private void measure(ComponentHealth component, java.util.function.BooleanSupplier probe) {
        Instant attempt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        long started = System.nanoTime();
        boolean ok;
        try { ok = probe.getAsBoolean(); } catch (RuntimeException error) { ok = false; }
        component.record(ok, (System.nanoTime() - started) / 1_000_000.0, ok ? null : "Operation failed",
                attempt, clock.instant().truncatedTo(ChronoUnit.MILLIS));
    }
    public Map<String, Object> snapshot() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("application", application.snapshot(now)); components.put("statusMonitor", status.getHealth(now));
        components.put("wakeTransport", wake.snapshot(now));
        var states = components.values().stream().map(value -> ((Map<?, ?>) value).get("status")).toList();
        boolean down = states.contains("down"), unknown = states.contains("unknown"), degraded = states.contains("degraded");
        String measured = components.values().stream().map(value -> ((Map<?, ?>) value).get("lastMeasurement"))
                .filter(java.util.Objects::nonNull).map(value -> Instant.parse(value.toString()))
                .max(Instant::compareTo).orElse(now).toString();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("contract", "application-health/v1");
        report.put("status", down ? "down" : unknown ? "unknown" : degraded ? "degraded" : "operational");
        report.put("coreAvailable", down ? Boolean.FALSE : unknown ? null : Boolean.TRUE);
        report.put("timestamp", measured); report.put("validForMs", 180_000); report.put("components", components);
        return report;
    }
}
