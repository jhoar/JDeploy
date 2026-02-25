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
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.WildcardParameter;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "infrastructure/subnet", layout = MainLayout.class)
@PageTitle("Subnet Detail")
@RolesAllowed({ApiRoles.EDITOR, ApiRoles.ADMIN})
public class SubnetDetailView extends VerticalLayout implements HasUrlParameter<String> {
    private final TopologyApiClient apiClient;
    private final VaadinActionAuthorizationService authorizationService;
    private final TextField cidr = new TextField("Subnet CIDR");
    private final TextField vlan = new TextField("VLAN");
    private final TextField routingZone = new TextField("Routing zone");
    private String original;

    public SubnetDetailView(TopologyApiClient apiClient, VaadinActionAuthorizationService authorizationService) {
        this.apiClient = apiClient;
        this.authorizationService = authorizationService;
        Button save = new Button("Save", e -> {
            if (cidr.isEmpty() || vlan.isEmpty() || routingZone.isEmpty()) {
                Notification.show("All fields are required");
                return;
            }
            try {
                authorizationService.assertCanEditTopology();
                apiClient.updateSubnet(original, new TopologyApiClient.SubnetUpdateRequest(cidr.getValue(), vlan.getValue(), routingZone.getValue()));
                original = cidr.getValue();
                Notification.show("Subnet updated");
            } catch (Exception ex) {
                Notification.show("Update failed: " + ex.getMessage(), 4000, Notification.Position.MIDDLE);
            }
        });
        Button cancel = new Button("Cancel", e -> setParameter(null, original));
        add(new H3("Subnet Detail"), new FormLayout(cidr, vlan, routingZone), new HorizontalLayout(save, cancel));
    }

    @Override
    public void setParameter(com.vaadin.flow.router.BeforeEvent event, @WildcardParameter String parameter) {
        if (parameter != null) {
            original = parameter;
        }
        if (original == null) {
            return;
        }
        try {
            TopologyApiClient.SubnetUpdateRequest req = apiClient.subnet(original);
            cidr.setValue(req.cidr());
            vlan.setValue(req.vlan());
            routingZone.setValue(req.routingZone());
            original = req.cidr();
        } catch (Exception ex) {
            Notification.show("Failed to load subnet: " + ex.getMessage(), 4000, Notification.Position.MIDDLE);
        }
    }
}
