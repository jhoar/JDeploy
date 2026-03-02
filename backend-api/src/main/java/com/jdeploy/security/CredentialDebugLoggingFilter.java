package com.jdeploy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

public class CredentialDebugLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(CredentialDebugLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        logAuthorizationHeader(request);
        filterChain.doFilter(request, response);
    }

    private void logAuthorizationHeader(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return;
        }

        logger.warn("[CREDENTIAL-DEBUG] backend-api inbound Authorization header: {}", authorization);
        if (!authorization.startsWith("Basic ")) {
            return;
        }

        String encoded = authorization.substring("Basic ".length()).trim();
        try {
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            logger.warn("[CREDENTIAL-DEBUG] backend-api inbound Basic credentials: {}", decoded);
        } catch (IllegalArgumentException ex) {
            logger.warn("[CREDENTIAL-DEBUG] backend-api inbound Basic credentials could not be decoded");
        }
    }
}
