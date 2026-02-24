package com.jdeploy.artifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalFilesystemArtifactStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void createReadDeleteLifecycleWorks() {
        LocalFilesystemArtifactStorage storage = new LocalFilesystemArtifactStorage(tempDir.toString());

        ArtifactMetadata created = storage.create("diagram.puml", "@startuml\n@enduml");
        ArtifactMetadata metadata = storage.readMetadata("diagram.puml");

        assertEquals("diagram.puml", created.artifactId());
        assertTrue(metadata.sizeBytes() > 0);
        assertTrue(storage.delete("diagram.puml"));
        assertFalse(storage.delete("diagram.puml"));
    }
}
