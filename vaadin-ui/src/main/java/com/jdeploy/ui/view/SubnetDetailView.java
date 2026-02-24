package com.jdeploy.ui.view;

import com.jdeploy.security.ApiRoles;
import com.jdeploy.ui.client.TopologyApiClient;
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
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;

import java.util.Set;
import java.util.stream.Collectors;

@Route(value = "infrastructure/subnet", layout = MainLayout.class)
@PageTitle("Subnet Detail")
@PermitAll
public class SubnetDetailView extends VerticalLayout implements HasUrlParameter<String> {
    private final TopologyApiClient apiClient;
    private final TextField cidr = new TextField("Subnet CIDR");
    private final TextField vlan = new TextField("VLAN");
    private final TextField routingZone = new TextField("Routing zone");
    private String original;

    public SubnetDetailView(TopologyApiClient apiClient, AuthenticationContext auth) {
        this.apiClient = apiClient;
        boolean canEdit = authorities(auth).stream().anyMatch(a -> a.equals(ApiRoles.EDITOR) || a.equals(ApiRoles.ADMIN));
        cidr.setReadOnly(!canEdit); vlan.setReadOnly(!canEdit); routingZone.setReadOnly(!canEdit);
        Button save = new Button("Save", e -> {
            if (cidr.isEmpty() || vlan.isEmpty() || routingZone.isEmpty()) { Notification.show("All fields are required"); return; }
            try {
                apiClient.updateSubnet(original, new TopologyApiClient.SubnetUpdateRequest(cidr.getValue(), vlan.getValue(), routingZone.getValue()));
                original = cidr.getValue();
                Notification.show("Subnet updated");
            } catch (Exception ex) {
                Notification.show("Update failed: " + ex.getMessage(), 4000, Notification.Position.MIDDLE);
            }
        });
        save.setEnabled(canEdit);
        Button cancel = new Button("Cancel", e -> setParameter(null, original));
        add(new H3("Subnet Detail"), new FormLayout(cidr, vlan, routingZone), new HorizontalLayout(save, cancel));
    }

    @Override
    public void setParameter(com.vaadin.flow.router.BeforeEvent event, @WildcardParameter String parameter) {
        if (parameter != null) { original = parameter; }
        if (original == null) { return; }
        try {
            TopologyApiClient.SubnetUpdateRequest req = apiClient.subnet(original);
            cidr.setValue(req.cidr()); vlan.setValue(req.vlan()); routingZone.setValue(req.routingZone()); original = req.cidr();
        } catch (Exception ex) {
            Notification.show("Failed to load subnet: " + ex.getMessage(), 4000, Notification.Position.MIDDLE);
        }
    }

    private Set<String> authorities(AuthenticationContext auth) {return auth.getAuthenticatedUser(org.springframework.security.core.userdetails.UserDetails.class).map(user -> user.getAuthorities().stream().map(Object::toString).collect(Collectors.toSet())).orElse(Set.of());}
}
