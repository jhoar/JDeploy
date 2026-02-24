package com.jdeploy.ui.view;

import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.WildcardParameter;
import jakarta.annotation.security.PermitAll;

@Route(value = "infrastructure/subnet", layout = MainLayout.class)
@PageTitle("Subnet Detail")
@PermitAll
public class SubnetDetailView extends VerticalLayout implements HasUrlParameter<String> {
    private final TextField cidr = new TextField("Subnet CIDR");

    public SubnetDetailView() {
        cidr.setReadOnly(true);
        add(new H3("Subnet Detail"), new FormLayout(cidr));
    }

    @Override
    public void setParameter(com.vaadin.flow.router.BeforeEvent event, @WildcardParameter String parameter) {
        cidr.setValue(parameter == null ? "" : parameter);
    }
}
