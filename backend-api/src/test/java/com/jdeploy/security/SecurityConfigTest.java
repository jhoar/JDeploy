package com.jdeploy.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SecurityConfigTest {

    @Test
    void validatorRejectsWeakPasswordsWhenPolicyEnabled() {
        SecurityCredentialsProperties.PasswordPolicy policy = new SecurityCredentialsProperties.PasswordPolicy();
        policy.setEnforce(true);
        policy.setMinLength(12);
        policy.setMinCharacterClasses(3);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SecurityCredentialPolicyValidator.validatePassword(
                        "jdeploy.security.users.reader.password",
                        "weakpass",
                        policy));

        assertEquals(
                "Credential 'jdeploy.security.users.reader.password' does not meet password policy: length must be at least 12 characters.",
                error.getMessage());
    }

    @Test
    void validatorAllowsWeakPasswordsWhenPolicyDisabled() {
        SecurityCredentialsProperties.PasswordPolicy policy = new SecurityCredentialsProperties.PasswordPolicy();
        policy.setEnforce(false);

        assertDoesNotThrow(() -> SecurityCredentialPolicyValidator.validatePassword(
                "jdeploy.security.users.reader.password",
                "weakpass",
                policy));
    }

    @Test
    void validatorRejectsInsufficientCharacterClasses() {
        SecurityCredentialsProperties.PasswordPolicy policy = new SecurityCredentialsProperties.PasswordPolicy();
        policy.setEnforce(true);
        policy.setMinLength(8);
        policy.setMinCharacterClasses(3);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SecurityCredentialPolicyValidator.validatePassword(
                        "jdeploy.security.users.reader.password",
                        "alllowercase123",
                        policy));

        assertEquals(
                "Credential 'jdeploy.security.users.reader.password' does not meet password policy: must include at least 3 character classes (lowercase, uppercase, digits, symbols).",
                error.getMessage());
    }

    @Test
    void validatorRejectsNullPasswordWithHelpfulError() {
        SecurityCredentialsProperties.PasswordPolicy policy = new SecurityCredentialsProperties.PasswordPolicy();
        policy.setEnforce(true);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SecurityCredentialPolicyValidator.validatePassword(
                        "jdeploy.security.users.reader.password",
                        null,
                        policy));

        assertEquals(
                "Credential 'jdeploy.security.users.reader.password' is not configured. Set the corresponding JDEPLOY_*_PASSWORD environment variable.",
                error.getMessage());
    }
}
