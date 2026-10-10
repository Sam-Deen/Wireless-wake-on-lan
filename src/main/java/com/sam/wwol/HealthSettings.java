package com.sam.wwol;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.function.Function;

@Component
public class HealthSettings {
    private final Function<String, String> values;
    @Autowired
    public HealthSettings(Environment environment) { values = environment::getProperty; }
    HealthSettings(Map<String, String> environment) { values = environment::get; }

    long number(String key, long fallback) {
        try {
            long value = Long.parseLong(values.apply(key));
            return value > 0 && value <= 604_800_000 ? value : fallback;
        } catch (NumberFormatException error) { return fallback; }
    }
    public long statusInterval() { return Math.min(86_400_000, Math.max(1000, number("HEALTH_STATUS_MONITOR_PROBE_MS", 10_000))); }
    public long applicationInterval() { return Math.min(86_400_000, Math.max(1000, number("HEALTH_APPLICATION_PROBE_MS", 30_000))); }
    public long wakeInterval() { return Math.min(86_400_000, Math.max(1000, number("HEALTH_WAKE_TRANSPORT_PROBE_MS", 30_000))); }
    public long pingTimeout() { return Math.min(30_000, number("HEALTH_PING_TIMEOUT_MS", 2000)); }
    ComponentHealth component(String prefix, long interval, long stale, long timeout, long slow) {
        return new ComponentHealth(interval, Math.max(number("HEALTH_" + prefix + "_STALE_MS", stale),
                2 * interval + timeout + 5000), number("HEALTH_" + prefix + "_SLOW_MS", slow),
                (int) Math.min(1_000_000, number("HEALTH_FAILURE_THRESHOLD", 1)),
                (int) Math.min(1_000_000, number("HEALTH_RECOVERY_THRESHOLD", 1)));
    }
}
