package com.jdeploy.ui.view;

import com.jdeploy.domain.HardwareNode;
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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Route(value = "infrastructure/node", layout = MainLayout.class)
@PageTitle("Node Detail")
@PermitAll
public class NodeDetailView extends VerticalLayout implements HasUrlParameter<String> {
    private final TopologyApiClient apiClient;
    private final TextField hostname = new TextField("Node hostname");
    private final TextField ipAddress = new TextField("IP address");
    private final TextField roles = new TextField("Roles (comma separated)");
    private final ComboBox<HardwareNode.NodeType> type = new ComboBox<>("Node type");
    private final Button save = new Button("Save");
    private String original;

    public NodeDetailView(TopologyApiClient apiClient, AuthenticationContext auth) {
        this.apiClient = apiClient;
        boolean canEdit = authorities(auth).stream().anyMatch(a -> a.equals(ApiRoles.EDITOR) || a.equals(ApiRoles.ADMIN));
        type.setItems(HardwareNode.NodeType.values());
        hostname.setReadOnly(!canEdit); ipAddress.setReadOnly(!canEdit); roles.setReadOnly(!canEdit); type.setReadOnly(!canEdit);
        save.setEnabled(canEdit);
        save.addClickListener(e -> {
            if (hostname.isEmpty() || ipAddress.isEmpty() || roles.isEmpty() || type.getValue() == null) {
                Notification.show("All fields are required", 3000, Notification.Position.MIDDLE); return;
            }
            try {
                apiClient.updateNode(original, new TopologyApiClient.HardwareNodeUpdateRequest(type.getValue(), hostname.getValue(), ipAddress.getValue(),
                        Arrays.stream(roles.getValue().split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet())));
                original = hostname.getValue();
                Notification.show("Node updated");
            } catch (Exception ex) {
                Notification.show("Update failed: " + ex.getMessage(), 4000, Notification.Position.MIDDLE);
            }
        });
        Button cancel = new Button("Cancel", e -> setParameter(null, original));
        add(new H3("Hardware Node Detail"), new FormLayout(type, hostname, ipAddress, roles), new HorizontalLayout(save, cancel));
    }

    @Override
    public void setParameter(com.vaadin.flow.router.BeforeEvent event, @WildcardParameter String parameter) {
        if (parameter != null) { original = parameter; }
        if (original == null) { return; }
        try {
            TopologyApiClient.HardwareNodeUpdateRequest req = apiClient.node(original);
            type.setValue(req.type()); hostname.setValue(req.hostname()); ipAddress.setValue(req.ipAddress()); roles.setValue(String.join(",", req.roles())); original = req.hostname();
        } catch (Exception ex) {
            Notification.show("Failed to load node: " + ex.getMessage(), 4000, Notification.Position.MIDDLE);
        }
    }

    private Set<String> authorities(AuthenticationContext auth) {return auth.getAuthenticatedUser(org.springframework.security.core.userdetails.UserDetails.class).map(user -> user.getAuthorities().stream().map(Object::toString).collect(Collectors.toSet())).orElse(Set.of());}
}
