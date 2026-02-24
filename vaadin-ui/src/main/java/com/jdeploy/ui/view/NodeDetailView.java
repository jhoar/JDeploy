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

@Route(value = "infrastructure/node", layout = MainLayout.class)
@PageTitle("Node Detail")
@PermitAll
public class NodeDetailView extends VerticalLayout implements HasUrlParameter<String> {
    private final TextField hostname = new TextField("Node hostname");

    public NodeDetailView() {
        hostname.setReadOnly(true);
        add(new H3("Hardware Node Detail"), new FormLayout(hostname));
    }

    @Override
    public void setParameter(com.vaadin.flow.router.BeforeEvent event, @WildcardParameter String parameter) {
        hostname.setValue(parameter == null ? "" : parameter);
    }
}
