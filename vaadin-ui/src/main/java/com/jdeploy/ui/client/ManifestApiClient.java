package com.jdeploy.ui.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ManifestApiClient {

    private final RestClient restClient;

    public ManifestApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public ManifestIngestResult ingest(String manifestYaml) {
        OperationResult response = restClient.post()
                .uri("/api/manifests/ingest")
                .contentType(MediaType.valueOf("application/x-yaml"))
                .body(manifestYaml)
                .retrieve()
                .body(OperationResult.class);
        if (response == null) {
            return new ManifestIngestResult("FAILED", 0, 0, "No response returned");
        }
        return new ManifestIngestResult(response.status(), null, null, response.message());
    }

    public record OperationResult(String status, String message) {
    }

    public record ManifestIngestResult(String status, Integer created, Integer updated, String errors) {
    }
}
