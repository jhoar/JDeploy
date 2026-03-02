package com.jdeploy.ui.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class CredentialDebugLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(CredentialDebugLoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws java.io.IOException {
        logAuthorizationHeader(request.getHeaders());
        return execution.execute(request, body);
    }

    private void logAuthorizationHeader(HttpHeaders headers) {
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return;
        }

        logger.warn("[CREDENTIAL-DEBUG] vaadin-ui outbound Authorization header: {}", authorization);
        if (!authorization.startsWith("Basic ")) {
            return;
        }

        String encoded = authorization.substring("Basic ".length()).trim();
        try {
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            logger.warn("[CREDENTIAL-DEBUG] vaadin-ui outbound Basic credentials: {}", decoded);
        } catch (IllegalArgumentException ex) {
            logger.warn("[CREDENTIAL-DEBUG] vaadin-ui outbound Basic credentials could not be decoded");
        }
    }
}
