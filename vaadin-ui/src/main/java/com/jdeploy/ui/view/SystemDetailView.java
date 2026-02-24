package com.jdeploy.ui.view;

import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.WildcardParameter;
import jakarta.annotation.security.PermitAll;

@Route(value = "infrastructure/system", layout = MainLayout.class)
@PageTitle("System Detail")
@PermitAll
public class SystemDetailView extends VerticalLayout implements HasUrlParameter<String> {
    private final TextField name = new TextField("System name");

    public SystemDetailView() {
        name.setReadOnly(true);
        add(new H3("Software System Detail"), new FormLayout(name));
    }

    @Override
    public void setParameter(com.vaadin.flow.router.BeforeEvent event, @WildcardParameter String parameter) {
        name.setValue(parameter == null ? "" : parameter);
    }
}
