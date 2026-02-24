package com.jdeploy.ui.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jdeploy.backend")
public record ApiClientConfiguration(String baseUrl) {

    public ApiClientConfiguration {
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:8080" : baseUrl;
    }
}
