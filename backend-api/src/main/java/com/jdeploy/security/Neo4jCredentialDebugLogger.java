package com.jdeploy.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class Neo4jCredentialDebugLogger {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jCredentialDebugLogger.class);

    private final CredentialDebugLoggingProperties properties;
    private final Environment environment;

    public Neo4jCredentialDebugLogger(CredentialDebugLoggingProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void logConfiguredNeo4jCredentials() {
        if (!properties.isEnabled()) {
            return;
        }

        logger.warn("[CREDENTIAL-DEBUG] backend-api Neo4j URI: {}", environment.getProperty("spring.neo4j.uri"));
        logger.warn("[CREDENTIAL-DEBUG] backend-api Neo4j username: {}", environment.getProperty("spring.neo4j.authentication.username"));
        logger.warn("[CREDENTIAL-DEBUG] backend-api Neo4j password: {}", environment.getProperty("spring.neo4j.authentication.password"));
    }
}
