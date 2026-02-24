package com.jdeploy.ui.client;

import com.jdeploy.artifact.ArtifactMetadata;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ArtifactApiClient {

    private final RestClient restClient;

    public ArtifactApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public ArtifactMetadata generate(String manifestYaml) {
        return restClient.post()
                .uri("/api/artifacts/generate")
                .contentType(MediaType.valueOf("application/x-yaml"))
                .body(manifestYaml)
                .retrieve()
                .body(ArtifactMetadata.class);
    }

    public byte[] download(String artifactId) {
        return restClient.get()
                .uri("/api/artifacts/{artifactId}", artifactId)
                .retrieve()
                .body(byte[].class);
    }

    public String downloadUrl(String artifactId) {
        return "/api/artifacts/" + artifactId;
    }
}
