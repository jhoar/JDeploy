package com.jdeploy.artifact;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class LocalFilesystemArtifactStorage implements ArtifactStorage {

    private final Path basePath;

    public LocalFilesystemArtifactStorage(@Value("${jdeploy.artifact.uml-output-path:examples/uml}") String basePath) {
        this.basePath = Path.of(Objects.requireNonNull(basePath, "basePath must not be null"));
    }

    @Override
    public ArtifactMetadata create(String artifactName, String content) {
        Objects.requireNonNull(artifactName, "artifactName must not be null");
        Objects.requireNonNull(content, "content must not be null");
        try {
            Files.createDirectories(basePath);
            Path artifactPath = basePath.resolve(artifactName);
            Files.writeString(artifactPath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return metadataFromPath(artifactPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write artifact to filesystem", ex);
        }
    }

    @Override
    public ArtifactMetadata readMetadata(String artifactId) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Path artifactPath = basePath.resolve(artifactId);
        if (!Files.exists(artifactPath)) {
            throw new IllegalArgumentException("Artifact does not exist: " + artifactId);
        }
        return metadataFromPath(artifactPath);
    }

    @Override
    public boolean delete(String artifactId) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Path artifactPath = basePath.resolve(artifactId);
        try {
            return Files.deleteIfExists(artifactPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete artifact " + artifactId, ex);
        }
    }

    @Override
    public List<String> expireOlderThan(Duration maxAge) {
        Objects.requireNonNull(maxAge, "maxAge must not be null");
        Instant cutoff = Instant.now().minus(maxAge);
        List<String> deleted = new ArrayList<>();

        if (!Files.isDirectory(basePath)) {
            return deleted;
        }

        try (var paths = Files.list(basePath)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    Instant lastModified = Files.getLastModifiedTime(path).toInstant();
                    if (lastModified.isBefore(cutoff) && Files.deleteIfExists(path)) {
                        deleted.add(path.getFileName().toString());
                    }
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to expire artifact " + path.getFileName(), ex);
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list artifacts for expiration", ex);
        }
        return deleted;
    }

    private ArtifactMetadata metadataFromPath(Path path) {
        try {
            var attrs = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
            return new ArtifactMetadata(
                    path.getFileName().toString(),
                    path,
                    attrs.size(),
                    attrs.creationTime().toInstant(),
                    attrs.lastModifiedTime().toInstant()
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed reading metadata for artifact " + path.getFileName(), ex);
        }
    }
}
