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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class LocalFilesystemArtifactStorage implements ArtifactStorage {

    private static final String RETENTION_SUFFIX = ".retention";

    private final Path basePath;

    public LocalFilesystemArtifactStorage(@Value("${jdeploy.artifact.uml-output-path:examples/uml}") String basePath) {
        this.basePath = Path.of(Objects.requireNonNull(basePath, "basePath must not be null"));
    }

    @Override
    public ArtifactMetadata create(String artifactName, String content, Duration retention) {
        Objects.requireNonNull(artifactName, "artifactName must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(retention, "retention must not be null");

        try {
            Files.createDirectories(basePath);
            Path artifactPath = resolveArtifactPath(artifactName);
            Files.writeString(artifactPath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Instant retentionUntil = Instant.now().plus(retention);
            Files.writeString(retentionPath(artifactPath), retentionUntil.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return metadataFromPath(artifactPath, retentionUntil);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write artifact to filesystem", ex);
        }
    }

    @Override
    public StoredArtifact read(String artifactId) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Path artifactPath = resolveArtifactPath(artifactId);
        if (!Files.exists(artifactPath)) {
            throw new ArtifactNotFoundException("Artifact does not exist: " + artifactId);
        }

        try {
            Instant retentionUntil = readRetention(artifactPath);
            if (retentionUntil != null && !retentionUntil.isAfter(Instant.now())) {
                delete(artifactId);
                throw new ArtifactExpiredException("Artifact has expired: " + artifactId);
            }
            String content = Files.readString(artifactPath);
            return new StoredArtifact(metadataFromPath(artifactPath, retentionUntil), content);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read artifact " + artifactId, ex);
        }
    }

    @Override
    public List<ArtifactMetadata> list() {
        if (!Files.isDirectory(basePath)) {
            return List.of();
        }

        try (var paths = Files.list(basePath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().endsWith(RETENTION_SUFFIX))
                    .map(path -> metadataFromPath(path, readRetention(path)))
                    .sorted(Comparator.comparing(ArtifactMetadata::artifactId))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list artifacts", ex);
        }
    }

    @Override
    public boolean delete(String artifactId) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Path artifactPath = resolveArtifactPath(artifactId);
        try {
            boolean deleted = Files.deleteIfExists(artifactPath);
            Files.deleteIfExists(retentionPath(artifactPath));
            return deleted;
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
            paths.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().endsWith(RETENTION_SUFFIX))
                    .forEach(path -> {
                        try {
                            Instant retentionUntil = readRetention(path);
                            if (retentionUntil != null && !retentionUntil.isAfter(cutoff) && Files.deleteIfExists(path)) {
                                Files.deleteIfExists(retentionPath(path));
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

    private Path resolveArtifactPath(String artifactId) {
        Path artifactPath = basePath.resolve(artifactId).normalize();
        if (!artifactPath.startsWith(basePath.normalize())) {
            throw new IllegalArgumentException("Artifact id is invalid: " + artifactId);
        }
        return artifactPath;
    }

    private Path retentionPath(Path artifactPath) {
        return artifactPath.resolveSibling(artifactPath.getFileName() + RETENTION_SUFFIX);
    }

    private Instant readRetention(Path artifactPath) {
        Path retentionPath = retentionPath(artifactPath);
        if (!Files.exists(retentionPath)) {
            return null;
        }

        try {
            return Instant.parse(Files.readString(retentionPath).trim());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed reading retention for artifact " + artifactPath.getFileName(), ex);
        }
    }

    private ArtifactMetadata metadataFromPath(Path path, Instant retentionUntil) {
        try {
            var attrs = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
            return new ArtifactMetadata(
                    path.getFileName().toString(),
                    path,
                    attrs.size(),
                    attrs.creationTime().toInstant(),
                    attrs.lastModifiedTime().toInstant(),
                    retentionUntil
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed reading metadata for artifact " + path.getFileName(), ex);
        }
    }
}
