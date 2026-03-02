package com.jdeploy.ui.view;

import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("login")
@PageTitle("JDeploy Login")
@AnonymousAllowed
public class LoginRoute extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm loginForm;

    public LoginRoute() {
        loginForm = new LoginForm();
        loginForm.setAction("/login");
        add(loginForm);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        loginForm.setError(event.getLocation()
                .getQueryParameters()
                .getParameters()
                .containsKey("error"));
    }
}
