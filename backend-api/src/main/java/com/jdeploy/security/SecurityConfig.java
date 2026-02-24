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
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/manifests/ingest").hasAuthority(ApiRoles.TOPOLOGY_INGEST)
                        .requestMatchers("/api/artifacts/**").hasAnyAuthority(ApiRoles.ARTIFACT_GENERATE, ApiRoles.READ_ONLY)
                        .requestMatchers("/api/**").hasAuthority(ApiRoles.READ_ONLY)
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(
            @Value("${jdeploy.security.users.ingest.username:ingest}") String ingestUser,
            @Value("${jdeploy.security.users.ingest.password:ingest-password}") String ingestPassword,
            @Value("${jdeploy.security.users.generator.username:generator}") String generatorUser,
            @Value("${jdeploy.security.users.generator.password:generator-password}") String generatorPassword,
            @Value("${jdeploy.security.users.reader.username:reader}") String readerUser,
            @Value("${jdeploy.security.users.reader.password:reader-password}") String readerPassword) {

        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        UserDetails ingest = User.withUsername(ingestUser)
                .password(encoder.encode(ingestPassword))
                .authorities(ApiRoles.TOPOLOGY_INGEST, ApiRoles.ARTIFACT_GENERATE, ApiRoles.READ_ONLY)
                .build();
        UserDetails generator = User.withUsername(generatorUser)
                .password(encoder.encode(generatorPassword))
                .authorities(ApiRoles.ARTIFACT_GENERATE, ApiRoles.READ_ONLY)
                .build();
        UserDetails reader = User.withUsername(readerUser)
                .password(encoder.encode(readerPassword))
                .authorities(ApiRoles.READ_ONLY)
                .build();

        return new InMemoryUserDetailsManager(ingest, generator, reader);
    }
}
