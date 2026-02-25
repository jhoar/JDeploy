package com.jdeploy.ui.view;

import com.jdeploy.ui.client.ArtifactApiClient;
import com.jdeploy.ui.client.TopologyApiClient;
import com.jdeploy.ui.security.VaadinActionAuthorizationService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.textfield.TextArea;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiagramViewTest {

    @Test
    void selectingSystemLoadsPreviewAndEnablesGenerationPath() {
        TopologyApiClient topologyApiClient = mock(TopologyApiClient.class);
        ArtifactApiClient artifactApiClient = mock(ArtifactApiClient.class);
        VaadinActionAuthorizationService authorizationService = mock(VaadinActionAuthorizationService.class);

        when(topologyApiClient.systems()).thenReturn(java.util.List.of(new TopologyApiClient.SoftwareSystemView("Billing", 2)));
        when(topologyApiClient.systemDiagram("Billing"))
                .thenReturn(new TopologyApiClient.SystemDiagramView("Billing", java.util.List.of("billing-api"), java.util.List.of("node-a-01")));

        DiagramView view = new DiagramView(topologyApiClient, artifactApiClient, authorizationService);

        Button generate = (Button) findById(view, DiagramView.GENERATE_BUTTON_ID).orElseThrow();
        ComboBox<String> selector = (ComboBox<String>) findById(view, DiagramView.SYSTEM_SELECTOR_ID).orElseThrow();
        TextArea manifest = (TextArea) findById(view, DiagramView.MANIFEST_TEXTAREA_ID).orElseThrow();
        Pre preview = (Pre) findById(view, DiagramView.PREVIEW_PANEL_ID).orElseThrow();

        assertFalse(generate.isEnabled());
        selector.setValue("Billing");

        verify(topologyApiClient).systemDiagram("Billing");
        assertTrue(preview.getText().contains("System: Billing"));
        assertTrue(manifest.getValue().contains("- name: Billing"));
        assertTrue(generate.isEnabled());
    }

    private Optional<Component> findById(Component root, String id) {
        if (id.equals(root.getId().orElse(null))) {
            return Optional.of(root);
        }
        Optional<Component> childMatch = root.getChildren()
                .map(child -> findById(child, id))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
        if (childMatch.isPresent()) {
            return childMatch;
        }
        return Optional.empty();
    }
}
