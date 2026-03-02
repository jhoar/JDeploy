package com.jdeploy.ui.security;

import com.jdeploy.security.CommonLogFormatAccessFilter;
import com.jdeploy.ui.view.LoginRoute;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class VaadinSecurityConfiguration {

    @Bean
    CommonLogFormatAccessFilter commonLogFormatAccessFilter() {
        return new CommonLogFormatAccessFilter();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.with(
                VaadinSecurityConfigurer.vaadin(),
                configurer -> configurer.loginView(LoginRoute.class));
        http.formLogin(login -> login.defaultSuccessUrl("/topology", true));
        http.addFilterAfter(commonLogFormatAccessFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
