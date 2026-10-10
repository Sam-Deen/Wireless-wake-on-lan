package com.sam.wwol;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.util.Set;

/** A quiet, bounded Docker healthcheck mode; it never starts Spring. */
public final class Healthcheck {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> STATUSES = Set.of("operational", "degraded", "down", "unknown");

    private Healthcheck() {}

    public static int run() {
        String port = System.getenv().getOrDefault("SERVER_PORT", "8888");
        ObjectNode report = measure("http://127.0.0.1:" + port + "/health/application");
        try {
            byte[] output = JSON.writeValueAsBytes(report);
            System.out.write(output);
            System.out.write('\n');
            System.out.flush();
        } catch (java.io.IOException error) {
            return 1;
        }
        return (report.path("status").asText().equals("operational")
                || report.path("status").asText().equals("degraded"))
                && report.path("coreAvailable").isBoolean()
                && report.path("coreAvailable").booleanValue() ? 0 : 1;
    }

    static ObjectNode measure(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(2000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            byte[] raw;
            try (var body = code >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                if (body == null) return unavailable();
                raw = body.readNBytes(4097);
            }
            return parse(raw);
        } catch (java.io.IOException | RuntimeException error) {
            return unavailable();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static ObjectNode parse(byte[] raw) {
        try {
            if (raw.length > 4096) return unavailable();
            JsonNode report = JSON.readTree(raw);
            if (!(report instanceof ObjectNode object)
                    || !report.path("contract").asText().equals("application-health/v1")
                    || !STATUSES.contains(report.path("status").asText())
                    || JSON.writeValueAsBytes(report).length + 1 > 4096) {
                return unavailable();
            }
            return object;
        } catch (java.io.IOException | RuntimeException error) {
            return unavailable();
        }
    }

    private static ObjectNode unavailable() {
        ObjectNode report = JSON.createObjectNode();
        report.put("contract", "application-health/v1");
        report.put("status", "down");
        report.put("coreAvailable", false);
        report.put("timestamp", Instant.now().toString());
        report.put("validForMs", 180_000);
        ObjectNode application = report.putObject("components").putObject("application");
        application.put("status", "down");
        application.put("critical", true);
        application.put("reason", "Health endpoint unavailable");
        return report;
    }
}
