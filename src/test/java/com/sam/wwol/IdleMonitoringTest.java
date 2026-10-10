package com.sam.wwol;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "HEALTH_APPLICATION_PROBE_MS=1000", "HEALTH_WAKE_TRANSPORT_PROBE_MS=1000", "HEALTH_STATUS_MONITOR_PROBE_MS=1000"})
class IdleMonitoringTest {
    @Autowired ApplicationHealthMonitor monitor;
    @Test
    void scheduledMeasurementsAdvanceWithoutAnyHttpOrPowerRequests() throws Exception {
        Instant before = Instant.now();
        long deadline = System.nanoTime() + 5_000_000_000L;
        Map<?, ?> application = null, wake = null;
        do {
            var components = (Map<?, ?>) monitor.snapshot().get("components");
            application = (Map<?, ?>) components.get("application");
            wake = (Map<?, ?>) components.get("wakeTransport");
            if (application.get("lastSuccess") != null && wake.get("lastSuccess") != null
                    && Instant.parse(application.get("lastSuccess").toString()).isAfter(before)
                    && Instant.parse(wake.get("lastSuccess").toString()).isAfter(before)) break;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        assertNotNull(application.get("lastSuccess"));
        assertTrue(Instant.parse(application.get("lastSuccess").toString()).isAfter(before));
        assertTrue(Instant.parse(wake.get("lastSuccess").toString()).isAfter(before));
    }
}
