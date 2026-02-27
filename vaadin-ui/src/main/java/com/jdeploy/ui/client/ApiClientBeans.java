package com.jdeploy.ui.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableConfigurationProperties(ApiClientConfiguration.class)
public class ApiClientBeans {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient restClient(RestClient.Builder builder, ApiClientConfiguration config) {
        return builder
                .baseUrl(config.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor((request, body, execution) -> {
                    applyAuthentication(request.getHeaders(), config.auth());
                    return execution.execute(request, body);
                })
                .build();
    }

    private void applyAuthentication(HttpHeaders headers, ApiClientConfiguration.AuthConfiguration auth) {
        switch (auth.mode()) {
            case NONE -> {
                // intentionally unauthenticated
            }
            case BASIC -> headers.setBasicAuth(auth.basic().username(), auth.basic().password());
            case PROPAGATE -> applyPropagation(headers, auth.propagation().source());
        }
    }

    private void applyPropagation(HttpHeaders headers, ApiClientConfiguration.TokenSource source) {
        switch (source) {
            case REQUEST_AUTHORIZATION_HEADER -> {
                String authorization = currentRequestHeader(HttpHeaders.AUTHORIZATION);
                if (authorization != null && !authorization.isBlank()) {
                    headers.set(HttpHeaders.AUTHORIZATION, authorization);
                }
            }
            case REQUEST_SESSION_COOKIE -> {
                String cookie = currentRequestHeader(HttpHeaders.COOKIE);
                if (cookie != null && !cookie.isBlank()) {
                    headers.set(HttpHeaders.COOKIE, cookie);
                }
            }
            case SECURITY_CONTEXT -> {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getCredentials() instanceof String credential && !credential.isBlank()) {
                    if (credential.startsWith("Bearer ") || credential.startsWith("Basic ")) {
                        headers.set(HttpHeaders.AUTHORIZATION, credential);
                    }
                }
            }
        }
    }

    private String currentRequestHeader(String headerName) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            return request.getHeader(headerName);
        }
        return null;
    }
}
