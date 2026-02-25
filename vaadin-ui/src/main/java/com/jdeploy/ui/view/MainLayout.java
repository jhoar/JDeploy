package com.jdeploy.ui.view;

import com.jdeploy.security.ApiRoles;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;

import java.util.Set;
import java.util.stream.Collectors;

@PermitAll
public class MainLayout extends AppLayout {

    public MainLayout(AuthenticationContext authenticationContext, AccessAnnotationChecker accessChecker) {
        H2 title = new H2("JDeploy");
        SideNav sideNav = new SideNav();
        Set<String> authorities = authenticationContext.getAuthenticatedUser(org.springframework.security.core.userdetails.UserDetails.class)
                .map(user -> user.getAuthorities().stream().map(Object::toString).collect(Collectors.toSet()))
                .orElse(Set.of());

        sideNav.addItem(new SideNavItem("Topology Dashboard", TopologyDashboardView.class));
        if (authorities.contains(ApiRoles.TOPOLOGY_INGEST)) {
            sideNav.addItem(new SideNavItem("Manifest Ingest", ManifestIngestView.class));
        }
        if (authorities.contains(ApiRoles.READ_ONLY)
                || authorities.contains(ApiRoles.EDITOR)
                || authorities.contains(ApiRoles.ADMIN)) {
            sideNav.addItem(new SideNavItem("Infrastructure Explorer", InfrastructureExplorerView.class));
        }
        if (authorities.contains(ApiRoles.ARTIFACT_GENERATE) || authorities.contains(ApiRoles.READ_ONLY)) {
            sideNav.addItem(new SideNavItem("Diagram", DiagramView.class));
        }

        if (!accessChecker.hasAccess(TopologyDashboardView.class)) {
            sideNav.setVisible(false);
        }

        addToDrawer(new VerticalLayout(title, sideNav));
    }
}
