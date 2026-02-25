package com.jdeploy.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/openapi.json", "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/manifests/ingest").hasAuthority(ApiRoles.TOPOLOGY_INGEST)
                        .requestMatchers("/api/artifacts/**").hasAnyAuthority(ApiRoles.ARTIFACT_GENERATE, ApiRoles.READ_ONLY)
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/topology/**").hasAnyAuthority(ApiRoles.EDITOR, ApiRoles.ADMIN)
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/topology/**").hasAnyAuthority(ApiRoles.EDITOR, ApiRoles.ADMIN)
                        .requestMatchers("/api/**").hasAnyAuthority(ApiRoles.READ_ONLY, ApiRoles.EDITOR, ApiRoles.ADMIN)
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(
            @Value("${jdeploy.security.users.ingest.username:}") String ingestUser,
            @Value("${jdeploy.security.users.ingest.password:}") String ingestPassword,
            @Value("${jdeploy.security.users.generator.username:}") String generatorUser,
            @Value("${jdeploy.security.users.generator.password:}") String generatorPassword,
            @Value("${jdeploy.security.users.reader.username:}") String readerUser,
            @Value("${jdeploy.security.users.reader.password:}") String readerPassword,
            @Value("${jdeploy.security.password-policy.enforce:true}") boolean enforcePasswordPolicy,
            @Value("${jdeploy.security.password-policy.min-length:12}") int minimumPasswordLength,
            @Value("${jdeploy.security.password-policy.min-character-classes:3}") int minimumCharacterClasses) {

        String validatedIngestUser = requireNonBlank(
                "jdeploy.security.users.ingest.username",
                "JDEPLOY_INGEST_USER",
                ingestUser);
        String validatedIngestPassword = requireStrongPassword(
                "jdeploy.security.users.ingest.password",
                "JDEPLOY_INGEST_PASSWORD",
                ingestPassword,
                enforcePasswordPolicy,
                minimumPasswordLength,
                minimumCharacterClasses);
        String validatedGeneratorUser = requireNonBlank(
                "jdeploy.security.users.generator.username",
                "JDEPLOY_GENERATOR_USER",
                generatorUser);
        String validatedGeneratorPassword = requireStrongPassword(
                "jdeploy.security.users.generator.password",
                "JDEPLOY_GENERATOR_PASSWORD",
                generatorPassword,
                enforcePasswordPolicy,
                minimumPasswordLength,
                minimumCharacterClasses);
        String validatedReaderUser = requireNonBlank(
                "jdeploy.security.users.reader.username",
                "JDEPLOY_READER_USER",
                readerUser);
        String validatedReaderPassword = requireStrongPassword(
                "jdeploy.security.users.reader.password",
                "JDEPLOY_READER_PASSWORD",
                readerPassword,
                enforcePasswordPolicy,
                minimumPasswordLength,
                minimumCharacterClasses);

        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        UserDetails ingest = User.withUsername(validatedIngestUser)
                .password(encoder.encode(validatedIngestPassword))
                .authorities(ApiRoles.ADMIN, ApiRoles.EDITOR, ApiRoles.TOPOLOGY_INGEST, ApiRoles.ARTIFACT_GENERATE, ApiRoles.READ_ONLY)
                .build();
        UserDetails generator = User.withUsername(validatedGeneratorUser)
                .password(encoder.encode(validatedGeneratorPassword))
                .authorities(ApiRoles.EDITOR, ApiRoles.ARTIFACT_GENERATE, ApiRoles.READ_ONLY)
                .build();
        UserDetails reader = User.withUsername(validatedReaderUser)
                .password(encoder.encode(validatedReaderPassword))
                .authorities(ApiRoles.READ_ONLY)
                .build();

        return new InMemoryUserDetailsManager(ingest, generator, reader);
    }

    static String requireNonBlank(String propertyName, String environmentVariable, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing required security credential '" + propertyName
                    + "'. Configure it via '" + environmentVariable + "'.");
        }
        return value;
    }

    static String requireStrongPassword(
            String propertyName,
            String environmentVariable,
            String password,
            boolean enforcePasswordPolicy,
            int minimumPasswordLength,
            int minimumCharacterClasses) {
        String validatedPassword = requireNonBlank(propertyName, environmentVariable, password);
        if (!enforcePasswordPolicy) {
            return validatedPassword;
        }

        if (validatedPassword.length() < minimumPasswordLength) {
            throw new IllegalStateException("Credential '" + propertyName + "' does not meet password policy: "
                    + "length must be at least " + minimumPasswordLength + " characters.");
        }

        int characterClasses = 0;
        if (validatedPassword.chars().anyMatch(Character::isLowerCase)) {
            characterClasses++;
        }
        if (validatedPassword.chars().anyMatch(Character::isUpperCase)) {
            characterClasses++;
        }
        if (validatedPassword.chars().anyMatch(Character::isDigit)) {
            characterClasses++;
        }
        if (validatedPassword.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) {
            characterClasses++;
        }

        if (characterClasses < minimumCharacterClasses) {
            throw new IllegalStateException("Credential '" + propertyName + "' does not meet password policy: "
                    + "must include at least " + minimumCharacterClasses + " character classes "
                    + "(lowercase, uppercase, digits, symbols).");
        }

        return validatedPassword;
    }
}
