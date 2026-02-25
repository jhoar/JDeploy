package com.jdeploy.ui.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiClientConfigurationValidationTest {

    @Test
    void startupFailsFastWhenBasicUsernameIsMissing() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> new ApiClientConfiguration(
                "http://backend.test",
                new ApiClientConfiguration.AuthConfiguration(
                        ApiClientConfiguration.AuthMode.BASIC,
                        new ApiClientConfiguration.BasicAuthConfiguration("", "reader-password"),
                        new ApiClientConfiguration.PropagationConfiguration(ApiClientConfiguration.TokenSource.REQUEST_AUTHORIZATION_HEADER))));

        assertTrue(thrown.getMessage().contains("jdeploy.backend.auth.basic.username"));
    }

    @Test
    void startupFailsFastWhenBasicPasswordIsMissing() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> new ApiClientConfiguration(
                "http://backend.test",
                new ApiClientConfiguration.AuthConfiguration(
                        ApiClientConfiguration.AuthMode.BASIC,
                        new ApiClientConfiguration.BasicAuthConfiguration("reader", ""),
                        new ApiClientConfiguration.PropagationConfiguration(ApiClientConfiguration.TokenSource.REQUEST_AUTHORIZATION_HEADER))));

        assertTrue(thrown.getMessage().contains("jdeploy.backend.auth.basic.password"));
    }
}
