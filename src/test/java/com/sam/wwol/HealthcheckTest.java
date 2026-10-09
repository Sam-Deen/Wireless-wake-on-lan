package com.sam.wwol;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthcheckTest {
    private static final byte[] DOWN = ("{\"contract\":\"application-health/v1\",\"status\":\"down\","
            + "\"coreAvailable\":false,\"timestamp\":\"2026-10-10T12:00:00Z\",\"validForMs\":90000,"
            + "\"components\":{\"statusMonitor\":{\"status\":\"down\",\"critical\":true}}}")
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void http503PreservesTheApplicationReport() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health/application", exchange -> {
            exchange.sendResponseHeaders(503, DOWN.length);
            try (var body = exchange.getResponseBody()) { body.write(DOWN); }
        });
        server.start();
        try {
            var report = Healthcheck.measure("http://127.0.0.1:" + server.getAddress().getPort() + "/health/application");
            assertEquals("down", report.path("status").asText());
            assertTrue(report.path("components").has("statusMonitor"));
            assertFalse(report.path("components").has("application"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void oversizedMalformedAndMultipleReportsAreRejected() {
        for (String body : new String[]{"x".repeat(4097), "<html>OK</html>", "[]", "null",
                "{\"status\":\"operational\"}", new String(DOWN, StandardCharsets.UTF_8) + "{}"}) {
            var report = Healthcheck.parse(body.getBytes(StandardCharsets.UTF_8));
            assertEquals("Health endpoint unavailable",
                    report.path("components").path("application").path("reason").asText());
        }
    }

    @Test
    void unavailableEndpointReturnsSafeContract() {
        var report = Healthcheck.measure("http://127.0.0.1:1/health/application");
        assertEquals("application-health/v1", report.path("contract").asText());
        assertEquals("down", report.path("status").asText());
        assertFalse(report.path("coreAvailable").booleanValue());
        assertFalse(report.toString().contains("127.0.0.1"));
    }
}
