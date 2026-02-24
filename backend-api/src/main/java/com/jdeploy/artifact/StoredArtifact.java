package com.jdeploy.artifact;

public record StoredArtifact(
        ArtifactMetadata metadata,
        String content
) {
}
