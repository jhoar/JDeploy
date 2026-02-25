package com.jdeploy.ui.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiClientConfigurationValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(ApiClientBeans.class, RestClientBuilderTestConfiguration.class);

    @Test
    void startupFailsFastWhenBasicUsernameIsMissing() {
        contextRunner
                .withPropertyValues(
                        "jdeploy.backend.base-url=http://backend.test",
                        "jdeploy.backend.auth.mode=BASIC",
                        "jdeploy.backend.auth.basic.username=",
                        "jdeploy.backend.auth.basic.password=reader-password")
                .run(context -> {
                    assertTrue(context.getStartupFailure() != null);
                    assertTrue(context.getStartupFailure().getMessage().contains("jdeploy.backend.auth.basic.username"));
                });
    }

    @Test
    void startupFailsFastWhenBasicPasswordIsMissing() {
        contextRunner
                .withPropertyValues(
                        "jdeploy.backend.base-url=http://backend.test",
                        "jdeploy.backend.auth.mode=BASIC",
                        "jdeploy.backend.auth.basic.username=reader",
                        "jdeploy.backend.auth.basic.password=")
                .run(context -> {
                    assertTrue(context.getStartupFailure() != null);
                    assertTrue(context.getStartupFailure().getMessage().contains("jdeploy.backend.auth.basic.password"));
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RestClientBuilderTestConfiguration {
        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }
}
