package com.jdeploy.ui.security;

import com.jdeploy.ui.view.LoginRoute;
import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
public class VaadinSecurityConfiguration extends VaadinWebSecurity {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        http.formLogin(login -> login.defaultSuccessUrl("/topology", true));
        setLoginView(http, LoginRoute.class);
    }
}
