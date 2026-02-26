package com.jdeploy.security;

import org.springframework.stereotype.Component;

@Component
public class SecurityCredentialPolicyValidator {

    SecurityCredentialPolicyValidator(SecurityCredentialsProperties properties) {
        SecurityCredentialsProperties.PasswordPolicy policy = properties.getPasswordPolicy();
        validatePassword("jdeploy.security.users.ingest.password", properties.getUsers().getIngest().getPassword(), policy);
        validatePassword("jdeploy.security.users.generator.password", properties.getUsers().getGenerator().getPassword(), policy);
        validatePassword("jdeploy.security.users.reader.password", properties.getUsers().getReader().getPassword(), policy);
    }

    static void validatePassword(
            String propertyName,
            String password,
            SecurityCredentialsProperties.PasswordPolicy policy) {

        if (!policy.isEnforce()) {
            return;
        }

        if (password.length() < policy.getMinLength()) {
            throw new IllegalStateException("Credential '" + propertyName + "' does not meet password policy: "
                    + "length must be at least " + policy.getMinLength() + " characters.");
        }

        int characterClasses = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) {
            characterClasses++;
        }
        if (password.chars().anyMatch(Character::isUpperCase)) {
            characterClasses++;
        }
        if (password.chars().anyMatch(Character::isDigit)) {
            characterClasses++;
        }
        if (password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) {
            characterClasses++;
        }

        if (characterClasses < policy.getMinCharacterClasses()) {
            throw new IllegalStateException("Credential '" + propertyName + "' does not meet password policy: "
                    + "must include at least " + policy.getMinCharacterClasses() + " character classes "
                    + "(lowercase, uppercase, digits, symbols).");
        }
    }
}
