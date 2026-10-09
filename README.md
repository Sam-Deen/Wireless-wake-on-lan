# Wireless Wake-on-LAN

A Spring Boot service on port 8888 with `GET /status`, `POST /power/on`, and
`POST /power/off`. The Linux container uses host networking to broadcast wake
packets. Configure `TARGET_IP` and `TARGET_MAC` to override the defaults in
`application.properties`.

Build and start from source:

```sh
docker compose up -d --build
```

The Docker build compiles the current source with Java 21 and runs the Java
tests. It does not use the checked-in `target` JARs. For local development use
Java 21 and `mvn test` / `mvn spring-boot:run`.

## Application health

`GET /health/application` returns HTTP 200 with an `application-health/v1`
report for Operational, Down, and Unknown results. It measures:

- `application`: the running HTTP handler and application status access.
- `statusMonitor`: the background ping worker's last completed measurement,
  latency, failure count, last attempt, and last success. An offline target is a
  valid measurement. An unavailable ping command, execution error, or timeout
  makes this critical component Down. Before the first measurement or after
  25 seconds without a new attempt, it is Unknown.
- `wakeTransport`: MAC configuration validation and opening the local UDP
  broadcast socket used by wake requests. This component is critical.

Health checks never wake or shut down the target. Socket readiness does not
prove packet delivery, and SSH login/remote shutdown are not actively tested.
Reports contain fixed reason strings without addresses, credentials, or raw
errors. Cached worker measurement timestamps are preserved.

Docker runs `java -jar /app/app.jar --healthcheck` every 30 seconds with a 30-second startup
period. The command reads the local endpoint, preserves JSON even on HTTP 503,
and prints only one compact JSON object under 4096 bytes. It exits 0 only for
Operational/Degraded with core availability; Down/Unknown exit 1. An unreachable
or malformed endpoint produces a Down report with `Health endpoint unavailable`.
`SERVER_PORT` can override the default port for both Spring and this command.

Add the container as a **Docker Container** monitor in Status Server, with a
60-second polling interval. Reports are valid for 90 seconds. Inspect the latest
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
