package com.jdeploy.ui.view;

import com.jdeploy.domain.ExecutionEnvironment;
import com.jdeploy.security.ApiRoles;
import com.jdeploy.ui.client.TopologyApiClient;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.WildcardParameter;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;

import java.util.Set;
import java.util.stream.Collectors;

@Route(value = "infrastructure/environment", layout = MainLayout.class)
@PageTitle("Environment Detail")
@PermitAll
public class EnvironmentDetailView extends VerticalLayout implements HasUrlParameter<String> {
    private final TopologyApiClient apiClient;
    private final TextField name = new TextField("Environment name");
    private final ComboBox<ExecutionEnvironment.EnvironmentType> type = new ComboBox<>("Environment type");
    private String original;

    public EnvironmentDetailView(TopologyApiClient apiClient, AuthenticationContext auth) {
        this.apiClient = apiClient;
        type.setItems(ExecutionEnvironment.EnvironmentType.values());
        boolean canEdit = authorities(auth).stream().anyMatch(a -> a.equals(ApiRoles.EDITOR) || a.equals(ApiRoles.ADMIN));
        name.setReadOnly(!canEdit); type.setReadOnly(!canEdit);
        Button save = new Button("Save", e -> {
            if (name.isEmpty() || type.getValue() == null) { Notification.show("All fields are required"); return; }
            try {
                apiClient.updateEnvironment(original, new TopologyApiClient.ExecutionEnvironmentUpdateRequest(name.getValue(), type.getValue()));
                original = name.getValue();
                Notification.show("Environment updated");
            } catch (Exception ex) {
                Notification.show("Update failed: " + ex.getMessage(), 4000, Notification.Position.MIDDLE);
            }
        });
        save.setEnabled(canEdit);
        Button cancel = new Button("Cancel", e -> setParameter(null, original));
        add(new H3("Execution Environment Detail"), new FormLayout(name, type), new HorizontalLayout(save, cancel));
    }

    @Override
    public void setParameter(com.vaadin.flow.router.BeforeEvent event, @WildcardParameter String parameter) {
        if (parameter != null) { original = parameter; }
        if (original == null) { return; }
        try {
            TopologyApiClient.ExecutionEnvironmentUpdateRequest req = apiClient.environment(original);
            name.setValue(req.name()); type.setValue(req.type()); original = req.name();
        } catch (Exception ex) {
            Notification.show("Failed to load environment: " + ex.getMessage(), 4000, Notification.Position.MIDDLE);
        }
    }

    private Set<String> authorities(AuthenticationContext auth) {return auth.getAuthenticatedUser(org.springframework.security.core.userdetails.UserDetails.class).map(user -> user.getAuthorities().stream().map(Object::toString).collect(Collectors.toSet())).orElse(Set.of());}
}
