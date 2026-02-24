package com.jdeploy.service;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jdeploy.service.dto.DeploymentManifestDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@Service
public class ManifestParserService {

    private final ObjectMapper yamlMapper;
    private final Counter ingestionErrorCounter;
    private final ObservationRegistry observationRegistry;

    public ManifestParserService(MeterRegistry meterRegistry,
                                 ObservationRegistry observationRegistry) {
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "observationRegistry must not be null");
        if (meterRegistry == null) {
            throw new PreconditionViolationException("meterRegistry is required");
        }
        this.ingestionErrorCounter = Counter.builder("jdeploy.ingestion.errors")
                .description("Number of manifest ingestion and parsing errors")
                .register(meterRegistry);
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    public DeploymentManifestDto parseManifest(String yamlText) {
        if (yamlText == null) {
            throw new PreconditionViolationException("yamlText is required");
        }
        if (yamlText.isBlank()) {
            throw new PreconditionViolationException("yamlText must not be blank");
        }
        try {
            DeploymentManifestDto manifest = Observation.createNotStarted("jdeploy.manifest.parse", observationRegistry)
                    .observeChecked(() -> yamlMapper.readValue(yamlText, DeploymentManifestDto.class));
            if (manifest == null) {
                throw new PostconditionViolationException("Parser produced null manifest");
            }
            return manifest;
        } catch (JsonProcessingException ex) {
            ingestionErrorCounter.increment();
            throw new IllegalArgumentException(buildParseMessage(ex), ex);
        } catch (Exception ex) {
            ingestionErrorCounter.increment();
            throw new IllegalArgumentException("Unable to parse deployment manifest yaml", ex);
        }
    }

    public DeploymentManifestDto parseManifest(Path manifestPath) {
        if (manifestPath == null) {
            throw new PreconditionViolationException("manifestPath is required");
        }
        try {
            return parseManifest(Files.readString(manifestPath));
        } catch (IOException ex) {
            ingestionErrorCounter.increment();
            throw new IllegalArgumentException("Unable to read deployment manifest file", ex);
        }
    }

    private String buildParseMessage(JsonProcessingException ex) {
        JsonLocation location = ex.getLocation();
        if (location == null) {
            return "Malformed manifest yaml: " + ex.getOriginalMessage();
        }
        return "Malformed manifest yaml at line %d, column %d: %s"
                .formatted(location.getLineNr(), location.getColumnNr(), ex.getOriginalMessage());
    }
}
