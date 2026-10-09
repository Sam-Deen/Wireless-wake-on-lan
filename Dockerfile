FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress package

FROM eclipse-temurin:21-jdk

RUN apt-get update && \
    apt-get install -y --no-install-recommends iputils-ping && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /build/target/wwol-0.0.1-SNAPSHOT.jar app.jar

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD ["java", "-jar", "/app/app.jar", "--healthcheck"]

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "app.jar"]
