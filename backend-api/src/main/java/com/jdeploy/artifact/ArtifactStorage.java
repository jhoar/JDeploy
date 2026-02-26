package com.jdeploy.artifact;

import java.time.Duration;
import java.util.List;

public interface ArtifactStorage {

    ArtifactMetadata create(String artifactName, String content, Duration retention);

    /**
     * Reads an artifact by id.
     *
     * @throws ArtifactNotFoundException when artifact is missing
     * @throws ArtifactExpiredException when artifact retention has elapsed
     */
    StoredArtifact read(String artifactId);

    List<ArtifactMetadata> list();

    boolean delete(String artifactId);

    /**
     * Deletes artifacts whose retention has elapsed by at least {@code maxAge}.
     */
    List<String> expireOlderThan(Duration maxAge);
}
