package com.jdeploy.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestParserServiceTest {

    @Test
    void parseManifestReportsYamlLocationForMalformedContent() {
        ManifestParserService parserService = new ManifestParserService(new SimpleMeterRegistry(), ObservationRegistry.create());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> parserService.parseManifest("systems:\n - name: billing\n   components: [\n"));

        assertTrue(exception.getMessage().contains("Malformed manifest yaml at line"));
    }
}
