package com.jdeploy.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SecurityConfigTest {

    @Test
    void requireNonBlankRejectsBlankValues() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SecurityConfig.requireNonBlank("jdeploy.security.users.reader.username", "JDEPLOY_READER_USER", "   "));

        assertEquals(
                "Missing required security credential 'jdeploy.security.users.reader.username'. Configure it via 'JDEPLOY_READER_USER'.",
                error.getMessage());
    }

    @Test
    void requireStrongPasswordRejectsWeakPasswordsWhenPolicyEnabled() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SecurityConfig.requireStrongPassword(
                        "jdeploy.security.users.reader.password",
                        "JDEPLOY_READER_PASSWORD",
                        "weakpass",
                        true,
                        12,
                        3));

        assertEquals(
                "Credential 'jdeploy.security.users.reader.password' does not meet password policy: length must be at least 12 characters.",
                error.getMessage());
    }

    @Test
    void requireStrongPasswordAllowsWeakPasswordsWhenPolicyDisabled() {
        String value = SecurityConfig.requireStrongPassword(
                "jdeploy.security.users.reader.password",
                "JDEPLOY_READER_PASSWORD",
                "weakpass",
                false,
                12,
                3);

        assertEquals("weakpass", value);
    }
}
