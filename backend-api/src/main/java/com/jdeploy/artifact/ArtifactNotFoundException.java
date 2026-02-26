package com.jdeploy.artifact;

public class ArtifactNotFoundException extends IllegalArgumentException {

    public ArtifactNotFoundException(String message) {
        super(message);
    }
}
