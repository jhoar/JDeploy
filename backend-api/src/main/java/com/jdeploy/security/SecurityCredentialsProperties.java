package com.jdeploy.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "jdeploy.security")
@Validated
public class SecurityCredentialsProperties {

    @Valid
    private final Users users = new Users();

    @Valid
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    public Users getUsers() {
        return users;
    }

    public PasswordPolicy getPasswordPolicy() {
        return passwordPolicy;
    }

    public static class Users {

        @Valid
        private final Account ingest = new Account();

        @Valid
        private final Account generator = new Account();

        @Valid
        private final Account reader = new Account();

        public Account getIngest() {
            return ingest;
        }

        public Account getGenerator() {
            return generator;
        }

        public Account getReader() {
            return reader;
        }
    }

    public static class Account {

        @NotBlank(message = "must not be blank; configure the corresponding JDEPLOY_*_USER environment variable")
        private String username;

        @NotBlank(message = "must not be blank; configure the corresponding JDEPLOY_*_PASSWORD environment variable")
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class PasswordPolicy {

        private boolean enforce = true;

        @Min(value = 1, message = "must be >= 1")
        @Max(value = 512, message = "must be <= 512")
        private int minLength = 12;

        @Min(value = 1, message = "must be >= 1")
        @Max(value = 4, message = "must be <= 4")
        private int minCharacterClasses = 3;

        public boolean isEnforce() {
            return enforce;
        }

        public void setEnforce(boolean enforce) {
            this.enforce = enforce;
        }

        public int getMinLength() {
            return minLength;
        }

        public void setMinLength(int minLength) {
            this.minLength = minLength;
        }

        public int getMinCharacterClasses() {
            return minCharacterClasses;
        }

        public void setMinCharacterClasses(int minCharacterClasses) {
            this.minCharacterClasses = minCharacterClasses;
        }
    }
}
