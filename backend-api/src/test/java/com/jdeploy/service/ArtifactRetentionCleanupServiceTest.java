package com.jdeploy.service;

import com.jdeploy.artifact.LocalFilesystemArtifactStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactRetentionCleanupServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void cleanupExpiredArtifactsDeletesExpiredArtifactsAndRetentionSidecars() throws Exception {
        LocalFilesystemArtifactStorage storage = new LocalFilesystemArtifactStorage(tempDir.toString());
        storage.create("cleanup-expired.puml", "expired", Duration.ofDays(1));

        Path artifactPath = tempDir.resolve("cleanup-expired.puml");
        Path retentionPath = tempDir.resolve("cleanup-expired.puml.retention");
        Files.writeString(retentionPath, Instant.now().minus(Duration.ofMinutes(2)).toString());

        ArtifactRetentionCleanupService service = new ArtifactRetentionCleanupService(storage, Duration.ZERO);
        service.cleanupExpiredArtifacts();

        assertFalse(Files.exists(artifactPath));
        assertFalse(Files.exists(retentionPath));
    }

    @Test
    void cleanupExpiredArtifactsKeepsNonExpiredArtifactsAccessible() {
        LocalFilesystemArtifactStorage storage = new LocalFilesystemArtifactStorage(tempDir.toString());
        storage.create("cleanup-active.puml", "active", Duration.ofMinutes(30));

        Path artifactPath = tempDir.resolve("cleanup-active.puml");
        Path retentionPath = tempDir.resolve("cleanup-active.puml.retention");

        ArtifactRetentionCleanupService service = new ArtifactRetentionCleanupService(storage, Duration.ZERO);
        service.cleanupExpiredArtifacts();

        assertTrue(Files.exists(artifactPath));
        assertTrue(Files.exists(retentionPath));
        assertTrue(storage.read("cleanup-active.puml").content().contains("active"));
    }
}
