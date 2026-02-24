package com.jdeploy.artifact;

import java.nio.file.Path;
import java.time.Instant;

public record ArtifactMetadata(
        String artifactId,
        Path path,
        long sizeBytes,
        Instant createdAt,
        Instant lastModifiedAt
) {
}
