package com.jdeploy.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OperationMetricsService {

    private final Counter ingestionRequestsCounter;
    private final Counter ingestionSuccessCounter;
    private final Counter ingestionErrorCounter;
    private final Counter artifactGenerationSuccessCounter;
    private final Counter artifactGenerationErrorCounter;

    private final AtomicLong lastIngestionSuccessEpochMillis = new AtomicLong(0);
    private final AtomicLong lastArtifactGenerationSuccessEpochMillis = new AtomicLong(0);

    public OperationMetricsService(MeterRegistry meterRegistry) {
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");

        this.ingestionRequestsCounter = Counter.builder("jdeploy.ingestion.requests")
                .description("Number of manifest ingestion requests")
                .register(meterRegistry);
        this.ingestionSuccessCounter = Counter.builder("jdeploy.ingestion.success")
                .description("Number of successful manifest ingestion operations")
                .register(meterRegistry);
        this.ingestionErrorCounter = Counter.builder("jdeploy.ingestion.errors")
                .description("Number of manifest ingestion and parsing errors")
                .register(meterRegistry);

        this.artifactGenerationSuccessCounter = Counter.builder("jdeploy.artifacts.generated")
                .description("Number of generated deployment diagram artifacts")
                .register(meterRegistry);
        this.artifactGenerationErrorCounter = Counter.builder("jdeploy.artifacts.errors")
                .description("Number of deployment diagram generation errors")
                .register(meterRegistry);

        Gauge.builder("jdeploy.ingestion.last.success.epoch.millis", lastIngestionSuccessEpochMillis, AtomicLong::doubleValue)
                .description("Epoch milliseconds of the latest successful manifest ingestion")
                .register(meterRegistry);

        Gauge.builder("jdeploy.artifacts.last.success.epoch.millis", lastArtifactGenerationSuccessEpochMillis, AtomicLong::doubleValue)
                .description("Epoch milliseconds of the latest successful artifact generation")
                .register(meterRegistry);
    }

    public void recordIngestionRequest() {
        ingestionRequestsCounter.increment();
    }

    public void recordIngestionSuccess() {
        ingestionSuccessCounter.increment();
        lastIngestionSuccessEpochMillis.set(Instant.now().toEpochMilli());
    }

    public void recordIngestionError() {
        ingestionErrorCounter.increment();
    }

    public void recordArtifactGenerationSuccess() {
        artifactGenerationSuccessCounter.increment();
        lastArtifactGenerationSuccessEpochMillis.set(Instant.now().toEpochMilli());
    }

    public void recordArtifactGenerationError() {
        artifactGenerationErrorCounter.increment();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ingestionRequests", ingestionRequestsCounter.count());
        snapshot.put("ingestionSuccess", ingestionSuccessCounter.count());
        snapshot.put("ingestionErrors", ingestionErrorCounter.count());
        snapshot.put("artifactGenerationSuccess", artifactGenerationSuccessCounter.count());
        snapshot.put("artifactGenerationErrors", artifactGenerationErrorCounter.count());
        snapshot.put("lastIngestionSuccessEpochMillis", lastIngestionSuccessEpochMillis.get());
        snapshot.put("lastArtifactGenerationSuccessEpochMillis", lastArtifactGenerationSuccessEpochMillis.get());
        return snapshot;
    }
}
