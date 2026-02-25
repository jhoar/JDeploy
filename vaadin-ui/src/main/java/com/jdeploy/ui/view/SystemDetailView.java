package com.jdeploy.ui.view;

import com.jdeploy.security.ApiRoles;
import com.jdeploy.ui.client.TopologyApiClient;
import com.jdeploy.ui.security.VaadinActionAuthorizationService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.WildcardParameter;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "infrastructure/system", layout = MainLayout.class)
@PageTitle("System Detail")
@RolesAllowed({ApiRoles.EDITOR, ApiRoles.ADMIN})
public class SystemDetailView extends VerticalLayout implements HasUrlParameter<String> {
    private final TopologyApiClient apiClient;
    private final VaadinActionAuthorizationService authorizationService;
    private final TextField name = new TextField("System name");
    private final Button save = new Button("Save");
    private final Button cancel = new Button("Cancel");
    private final BeanValidationBinder<TopologyApiClient.SoftwareSystemUpdateRequest> binder = new BeanValidationBinder<>(TopologyApiClient.SoftwareSystemUpdateRequest.class);
    private String original;

    public SystemDetailView(TopologyApiClient apiClient, VaadinActionAuthorizationService authorizationService) {
        this.apiClient = apiClient;
        this.authorizationService = authorizationService;
        binder.bind(name, TopologyApiClient.SoftwareSystemUpdateRequest::name, null);

        save.addClickListener(e -> {
            if (name.isInvalid() || name.isEmpty()) {
                name.setErrorMessage("Name is required");
                name.setInvalid(true);
                return;
            }
            try {
                authorizationService.assertCanEditTopology();
                apiClient.updateSystem(original, new TopologyApiClient.SoftwareSystemUpdateRequest(name.getValue()));
                original = name.getValue();
                Notification.show("System updated");
            } catch (Exception ex) {
                Notification.show("Update failed: " + ex.getMessage(), 4000, Notification.Position.MIDDLE);
            }
        });
        cancel.addClickListener(e -> name.setValue(original == null ? "" : original));

        add(new H3("Software System Detail"), new FormLayout(name), new HorizontalLayout(save, cancel));
    }

    @Override
    public void setParameter(com.vaadin.flow.router.BeforeEvent event, @WildcardParameter String parameter) {
        original = parameter == null ? "" : parameter;
        try {
            TopologyApiClient.SoftwareSystemUpdateRequest req = apiClient.system(original);
            name.setValue(req.name());
            original = req.name();
            name.setInvalid(false);
        } catch (Exception ex) {
            Notification.show("Failed to load system: " + ex.getMessage(), 4000, Notification.Position.MIDDLE);
            name.setValue(original);
        }
    }
}
