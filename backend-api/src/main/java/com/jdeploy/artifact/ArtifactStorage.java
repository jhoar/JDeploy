package com.jdeploy.artifact;

import java.time.Duration;
import java.util.List;

public interface ArtifactStorage {

    ArtifactMetadata create(String artifactName, String content);

    ArtifactMetadata readMetadata(String artifactId);

    boolean delete(String artifactId);

    List<String> expireOlderThan(Duration maxAge);
}
