package com.jdeploy.artifact;

public class ArtifactExpiredException extends ArtifactNotFoundException {

    public ArtifactExpiredException(String message) {
        super(message);
    }
}
