package com.sam.wwol;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ApplicationHealthController {
    private final StatusService status;
    private final PowerService power;

    public ApplicationHealthController(StatusService status, PowerService power) {
        this.status = status;
        this.power = power;
    }

    @GetMapping(value = "/health/application", produces = "application/json")
    public Map<String, Object> health() {
        long started = System.nanoTime();
        Instant now = Instant.now();
        Map<String, Object> components = new LinkedHashMap<>();
        // Dispatch through the running HTTP application and read its status state.
        status.getStatus();
        var application = component("operational", null);
        application.put("latencyMs", (System.nanoTime() - started) / 1_000_000.0);
        components.put("application", application);

        StatusService.Measurement latest = status.getMeasurement();
        String monitorStatus;
        String monitorReason;
        if (latest == null) {
            monitorStatus = "unknown";
            monitorReason = "Not measured";
        } else if (Duration.between(latest.lastAttempt(), now).toMillis() > 25_000) {
            monitorStatus = "unknown";
            monitorReason = "Monitoring result stale";
        } else {
            monitorStatus = latest.consecutiveFailures() == 0 ? "operational" : "down";
            monitorReason = latest.reason();
        }
        var monitor = component(monitorStatus, monitorReason);
        if (latest != null) {
            monitor.put("lastAttempt", latest.lastAttempt().toString());
            monitor.put("lastSuccess", latest.lastSuccess() == null ? null : latest.lastSuccess().toString());
            monitor.put("latencyMs", latest.latencyMs());
            monitor.put("consecutiveFailures", latest.consecutiveFailures());
        }
        components.put("statusMonitor", monitor);

        boolean wakeAvailable = power.wakeTransportAvailable();
        components.put("wakeTransport", component(wakeAvailable ? "operational" : "down",
                wakeAvailable ? null : "Operation failed"));

        boolean down = !wakeAvailable || monitorStatus.equals("down");
        boolean unknown = !down && monitorStatus.equals("unknown");
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("contract", "application-health/v1");
        report.put("status", down ? "down" : unknown ? "unknown" : "operational");
        report.put("coreAvailable", down ? Boolean.FALSE : unknown ? null : Boolean.TRUE);
        report.put("timestamp", now.toString());
        report.put("validForMs", 90_000);
        report.put("components", components);
        return report;
    }

    private static Map<String, Object> component(String status, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("critical", true);
        result.put("reason", reason);
        return result;
    }
}
