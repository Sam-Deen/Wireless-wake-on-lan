# Wireless Wake-on-LAN

A Spring Boot service on port 8888 with `GET /status`, `POST /power/on`, and
`POST /power/off`. The Linux container uses host networking to broadcast wake
packets. Configure `TARGET_IP` and `TARGET_MAC` to override the defaults in
`application.properties`.

Set `DOCKERHUB_USERNAME` in your environment or a `.env` file to the Docker Hub
account used by the publishing workflow, then pull and start the published image:

```sh
docker compose up -d --pull always
```

The Docker build compiles the current source with Java 21 and runs the Java
tests. It does not use the checked-in `target` JARs. For local development use
Java 21 and `mvn test` / `mvn spring-boot:run`.

## Application health

`GET /health/application` returns HTTP 200 with an `application-health/v1`
report for Operational, Down, and Unknown results. It measures:

- `application`: scheduled application status access, independent of HTTP traffic.
  Docker's external Java command separately verifies HTTP dispatch.
- `statusMonitor`: the background ping worker's last completed measurement,
  latency, failure count, last attempt, and last success. An offline target is a
  valid measurement. An unavailable ping command, execution error, or timeout
  makes this critical component Down. Before the first measurement or after
  30 seconds without a completed measurement, it is Unknown by default.
- `wakeTransport`: MAC configuration validation and opening the local UDP
  broadcast socket used by wake requests. This component is critical.

Health checks never wake or shut down the target. Socket readiness does not
prove packet delivery, and SSH login/remote shutdown are not actively tested.
Reports contain fixed reason strings without addresses, credentials, or raw
errors. Cached worker measurement timestamps are preserved.

The status monitor runs every 10 seconds; application and wake transport checks
run every 30 seconds on the existing Spring scheduler. Fixed-delay tasks never
overlap and individual failures are recorded rather than terminating monitoring.
Health requests read cached results and do not open sockets or renew timestamps.
Every component includes attempt, success, failure, measurement, and status-change
times. Slow successful checks are Degraded; an offline target remains a valid
measurement and does not imply service failure.

Configure `HEALTH_STATUS_MONITOR_PROBE_MS`, `HEALTH_APPLICATION_PROBE_MS`, and
`HEALTH_WAKE_TRANSPORT_PROBE_MS`, with corresponding `*_STALE_MS` defaults of
30000, 90000, and 90000, and `*_SLOW_MS` defaults of 1500, 250, and 100.
`HEALTH_PING_TIMEOUT_MS` defaults to 2000. Values are milliseconds; effective
freshness is at least twice the interval plus the timeout and five seconds of
scheduling margin. `HEALTH_FAILURE_THRESHOLD` and `HEALTH_RECOVERY_THRESHOLD`
default to 1, preserving immediate failure/recovery, and can be raised. Published
component freshness allows a 180-second Docker/proxy delivery window while local
freshness is checked before publishing. There is no monitoring disk state.

Docker runs `java -jar /app/app.jar --healthcheck` every 30 seconds with a 30-second startup
period. The command reads the local endpoint, preserves JSON even on HTTP 503,
and prints only one compact JSON object under 4096 bytes. It exits 0 only for
Operational/Degraded with core availability; Down/Unknown exit 1. An unreachable
or malformed endpoint produces a Down report with `Health endpoint unavailable`.
`SERVER_PORT` can override the default port for both Spring and this command.

Add the container as a **Docker Container** monitor in Status Server, with a
60-second polling interval. Reports are valid for 180 seconds to cover cached
measurement age, Docker/proxy intervals, and scheduling margin. Inspect the latest
output with:

```sh
docker inspect --format '{{json .State.Health}}' wwol
```

The healthcheck is implemented entirely in Java and uses the application's
existing JSON dependencies. The `--healthcheck` mode exits without starting
another Spring server. Run all healthcheck and worker regression tests with:

```sh
mvn test
```
