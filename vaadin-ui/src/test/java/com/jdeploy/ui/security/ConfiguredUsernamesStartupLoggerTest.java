package com.jdeploy.ui.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfiguredUsernamesStartupLoggerTest {

    @Test
    void collectsOnlyConfiguredNonBlankUsernames() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.security.user.name", "ui-user")
                .withProperty("jdeploy.security.users.ingest.username", "ingest-user")
                .withProperty("jdeploy.security.users.reader.username", "   ")
                .withProperty("jdeploy.backend.auth.basic.username", "reader");

        Map<String, String> usernames = ConfiguredUsernamesStartupLogger.configuredUsernames(environment);

        assertEquals(Map.of(
                "spring.security.user.name", "ui-user",
                "jdeploy.security.users.ingest.username", "ingest-user",
                "jdeploy.backend.auth.basic.username", "reader"
        ), usernames);
    }
}
