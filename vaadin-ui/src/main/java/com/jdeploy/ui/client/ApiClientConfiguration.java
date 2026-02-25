package com.jdeploy.ui.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UI client settings for connecting to backend-api.
 * <p>
 * Properties under {@code jdeploy.backend.*} configure endpoint and auth behavior.
 */
@ConfigurationProperties(prefix = "jdeploy.backend")
public record ApiClientConfiguration(
        String baseUrl,
        AuthConfiguration auth
) {

    public ApiClientConfiguration {
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:8080" : baseUrl;
        auth = auth == null ? AuthConfiguration.defaults() : auth.normalized();
        auth.validate();
    }

    public enum AuthMode {
        NONE,
        BASIC,
        PROPAGATE
    }

    public enum TokenSource {
        REQUEST_AUTHORIZATION_HEADER,
        REQUEST_SESSION_COOKIE,
        SECURITY_CONTEXT
    }

    public record AuthConfiguration(
            AuthMode mode,
            BasicAuthConfiguration basic,
            PropagationConfiguration propagation
    ) {
        static AuthConfiguration defaults() {
            return new AuthConfiguration(
                    AuthMode.BASIC,
                    new BasicAuthConfiguration("reader", "reader-password"),
                    new PropagationConfiguration(TokenSource.REQUEST_AUTHORIZATION_HEADER));
        }

        AuthConfiguration normalized() {
            AuthMode normalizedMode = mode == null ? AuthMode.BASIC : mode;
            BasicAuthConfiguration normalizedBasic = basic == null ? new BasicAuthConfiguration(null, null) : basic;
            PropagationConfiguration normalizedPropagation = propagation == null
                    ? new PropagationConfiguration(TokenSource.REQUEST_AUTHORIZATION_HEADER)
                    : propagation;
            return new AuthConfiguration(normalizedMode, normalizedBasic, normalizedPropagation);
        }

        void validate() {
            if (mode == AuthMode.BASIC) {
                if (basic == null || basic.username() == null || basic.username().isBlank()) {
                    throw new IllegalStateException("jdeploy.backend.auth.basic.username is required when auth mode is BASIC");
                }
                if (basic.password() == null || basic.password().isBlank()) {
                    throw new IllegalStateException("jdeploy.backend.auth.basic.password is required when auth mode is BASIC");
                }
            }
            if (mode == AuthMode.PROPAGATE && (propagation == null || propagation.source() == null)) {
                throw new IllegalStateException("jdeploy.backend.auth.propagation.source is required when auth mode is PROPAGATE");
            }
        }
    }

    public record BasicAuthConfiguration(String username, String password) {
    }

    public record PropagationConfiguration(TokenSource source) {
    }
}
