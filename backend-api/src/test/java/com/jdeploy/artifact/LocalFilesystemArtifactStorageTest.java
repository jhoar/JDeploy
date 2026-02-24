package com.jdeploy.artifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

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
}
