package com.jdeploy.ui.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ConfiguredUsernamesStartupLogger implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ConfiguredUsernamesStartupLogger.class);

    private static final String[] CANDIDATE_PROPERTIES = {
            "spring.security.user.name",
            "jdeploy.security.users.ingest.username",
            "jdeploy.security.users.generator.username",
            "jdeploy.security.users.reader.username",
            "jdeploy.backend.auth.basic.username"
    };

    private final Environment environment;

    public ConfiguredUsernamesStartupLogger(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        configuredUsernames(environment).forEach((property, username) ->
                logger.info("Configured username [{}] = {}", property, username));
    }

    static Map<String, String> configuredUsernames(Environment environment) {
        Map<String, String> usernames = new LinkedHashMap<>();
        for (String property : CANDIDATE_PROPERTIES) {
            String value = environment.getProperty(property);
            if (value != null && !value.isBlank()) {
                usernames.put(property, value);
            }
        }
        return usernames;
    }
}
