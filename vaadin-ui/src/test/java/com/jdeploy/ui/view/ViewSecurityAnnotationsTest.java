package com.jdeploy.ui.view;

import com.jdeploy.security.ApiRoles;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewSecurityAnnotationsTest {

    @Test
    void loginRouteIsAnonymous() {
        assertNotNull(LoginRoute.class.getAnnotation(AnonymousAllowed.class));
    }

    @Test
    void sharedAuthenticatedViewsRemainPermitAll() {
        assertNotNull(MainLayout.class.getAnnotation(PermitAll.class));
        assertNotNull(TopologyDashboardView.class.getAnnotation(PermitAll.class));
    }

    @Test
    void ingestRouteRequiresIngestRole() {
        RolesAllowed rolesAllowed = ManifestIngestView.class.getAnnotation(RolesAllowed.class);
        assertNotNull(rolesAllowed);
        assertArrayEquals(new String[]{ApiRoles.TOPOLOGY_INGEST}, rolesAllowed.value());
    }

    @Test
    void diagramAndReadRoutesRequireExpectedRoles() {
        RolesAllowed diagramRoles = DiagramView.class.getAnnotation(RolesAllowed.class);
        assertNotNull(diagramRoles);
        assertTrue(hasSameEntries(diagramRoles.value(), ApiRoles.ARTIFACT_GENERATE, ApiRoles.READ_ONLY));

        RolesAllowed explorerRoles = InfrastructureExplorerView.class.getAnnotation(RolesAllowed.class);
        assertNotNull(explorerRoles);
        assertTrue(hasSameEntries(explorerRoles.value(), ApiRoles.READ_ONLY, ApiRoles.EDITOR, ApiRoles.ADMIN));
    }

    @Test
    void detailRoutesRequireEditorOrAdmin() {
        assertEditorAdmin(SystemDetailView.class);
        assertEditorAdmin(NodeDetailView.class);
        assertEditorAdmin(SubnetDetailView.class);
        assertEditorAdmin(EnvironmentDetailView.class);
    }

    private static void assertEditorAdmin(Class<?> viewClass) {
        RolesAllowed rolesAllowed = viewClass.getAnnotation(RolesAllowed.class);
        assertNotNull(rolesAllowed);
        assertTrue(hasSameEntries(rolesAllowed.value(), ApiRoles.EDITOR, ApiRoles.ADMIN));
    }

    private static boolean hasSameEntries(String[] actual, String... expected) {
        return java.util.Set.copyOf(java.util.Arrays.asList(actual))
                .equals(java.util.Set.copyOf(java.util.Arrays.asList(expected)));
    }
}
