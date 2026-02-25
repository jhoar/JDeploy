package com.jdeploy.ui.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UI client settings for connecting to backend-api.
 * <p>
 * Property: {@code jdeploy.backend.base-url}.
 */
@ConfigurationProperties(prefix = "jdeploy.backend")
public record ApiClientConfiguration(String baseUrl) {

    public ApiClientConfiguration {
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:8080" : baseUrl;
    }
}
