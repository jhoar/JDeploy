package com.jdeploy.artifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalFilesystemArtifactStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void createReadListDeleteLifecycleWorks() {
        LocalFilesystemArtifactStorage storage = new LocalFilesystemArtifactStorage(tempDir.toString());

        ArtifactMetadata created = storage.create("diagram.puml", "@startuml\n@enduml", Duration.ofDays(2));
        StoredArtifact storedArtifact = storage.read("diagram.puml");

        assertEquals("diagram.puml", created.artifactId());
        assertTrue(storedArtifact.metadata().sizeBytes() > 0);
        assertTrue(storedArtifact.content().contains("@startuml"));
        assertEquals(1, storage.list().size());
        assertNotNull(storedArtifact.metadata().retentionUntil());

        assertTrue(storage.delete("diagram.puml"));
        assertFalse(storage.delete("diagram.puml"));
        assertTrue(storage.list().isEmpty());
    }

    @Test
    void readRejectsExpiredArtifactsAndDeletesArtifactAndRetentionSidecar() throws Exception {
        LocalFilesystemArtifactStorage storage = new LocalFilesystemArtifactStorage(tempDir.toString());
        storage.create("expired.puml", "@startuml\nA->B\n@enduml", Duration.ofDays(1));

        Path artifactPath = tempDir.resolve("expired.puml");
        Path retentionPath = tempDir.resolve("expired.puml.retention");
        Files.writeString(retentionPath, Instant.now().minus(Duration.ofMinutes(1)).toString());

        assertThrows(ArtifactExpiredException.class, () -> storage.read("expired.puml"));
        assertFalse(Files.exists(artifactPath));
        assertFalse(Files.exists(retentionPath));
    }

    @Test
    void expireOlderThanUsesRetentionUntilAndDeletesOnlyExpiredArtifacts() throws Exception {
        LocalFilesystemArtifactStorage storage = new LocalFilesystemArtifactStorage(tempDir.toString());
        storage.create("expired-by-retention.puml", "expired", Duration.ofDays(1));
        storage.create("active-by-retention.puml", "active", Duration.ofDays(1));

        Path expiredRetention = tempDir.resolve("expired-by-retention.puml.retention");
        Path activeRetention = tempDir.resolve("active-by-retention.puml.retention");
        Files.writeString(expiredRetention, Instant.now().minus(Duration.ofMinutes(5)).toString());
        Files.writeString(activeRetention, Instant.now().plus(Duration.ofMinutes(30)).toString());

        List<String> deleted = storage.expireOlderThan(Duration.ZERO);

        assertEquals(List.of("expired-by-retention.puml"), deleted);
        assertFalse(Files.exists(tempDir.resolve("expired-by-retention.puml")));
        assertFalse(Files.exists(expiredRetention));
        assertTrue(Files.exists(tempDir.resolve("active-by-retention.puml")));
        assertTrue(Files.exists(activeRetention));
        assertDoesNotThrow(() -> storage.read("active-by-retention.puml"));
    }
}
