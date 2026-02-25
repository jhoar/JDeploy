package com.jdeploy.ui.view;

import com.jdeploy.artifact.ArtifactMetadata;
import com.jdeploy.security.ApiRoles;
import com.jdeploy.ui.client.ArtifactApiClient;
import com.jdeploy.ui.client.TopologyApiClient;
import com.jdeploy.ui.security.VaadinActionAuthorizationService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;

@Route(value = "diagram", layout = MainLayout.class)
@PageTitle("Diagram")
@RolesAllowed({ApiRoles.ARTIFACT_GENERATE, ApiRoles.READ_ONLY})
public class DiagramView extends VerticalLayout {

    static final String SYSTEM_SELECTOR_ID = "diagram-system-selector";
    static final String MANIFEST_TEXTAREA_ID = "diagram-manifest-textarea";
    static final String PREVIEW_PANEL_ID = "diagram-preview-panel";
    static final String GENERATE_BUTTON_ID = "diagram-generate-button";

    public DiagramView(TopologyApiClient topologyApiClient,
                       ArtifactApiClient artifactApiClient,
                       VaadinActionAuthorizationService authorizationService) {
        add(new H3("Diagram Generator (system-guided manifest mode)"));

        ComboBox<String> systemSelector = new ComboBox<>("System to model");
        systemSelector.setId(SYSTEM_SELECTOR_ID);
        systemSelector.setItems(topologyApiClient.systems().stream().map(TopologyApiClient.SoftwareSystemView::name).toList());
        systemSelector.setRequired(true);
        systemSelector.setRequiredIndicatorVisible(true);
        systemSelector.setHelperText("Select a system to load deployment context, then review or edit the generated YAML before producing the artifact.");

        Pre systemPreview = new Pre("Select a system to load deployment context.");
        systemPreview.setId(PREVIEW_PANEL_ID);

        TextArea manifestYaml = new TextArea("Deployment manifest YAML");
        manifestYaml.setId(MANIFEST_TEXTAREA_ID);
        manifestYaml.setWidthFull();
        manifestYaml.setMinHeight("240px");
        manifestYaml.setHelperText("This YAML is prefilled from the selected system context and can be edited before generation.");

        Pre metadataPanel = new Pre("No artifact generated yet.");
        Anchor download = new Anchor();
        download.setText("Download artifact");
        download.setVisible(false);

        Button generate = new Button("Generate", e -> {
            try {
                authorizationService.assertCanGenerateArtifacts();
                if (systemSelector.getValue() == null || systemSelector.getValue().isBlank()) {
                    Notification.show("Select a system before generation.");
                    return;
                }
                if (manifestYaml.getValue().isBlank()) {
                    Notification.show("Manifest YAML is required.");
                    return;
                }
                ArtifactMetadata metadata = artifactApiClient.generate(manifestYaml.getValue());
                metadataPanel.setText("artifactId: " + metadata.artifactId() + "\nsizeBytes: " + metadata.sizeBytes() +
                        "\ncreatedAt: " + metadata.createdAt() + "\nretentionUntil: " + metadata.retentionUntil());
                download.setHref(artifactApiClient.downloadUrl(metadata.artifactId()));
                download.setVisible(true);
            } catch (Exception ex) {
                Notification.show("Failed to generate diagram: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });
        generate.setId(GENERATE_BUTTON_ID);
        generate.setEnabled(false);

        systemSelector.addValueChangeListener(event -> {
            String systemName = event.getValue();
            if (systemName == null || systemName.isBlank()) {
                systemPreview.setText("Select a system to load deployment context.");
                manifestYaml.clear();
                updateGenerateEnabledState(generate, systemSelector, manifestYaml);
                return;
            }
            try {
                TopologyApiClient.SystemDiagramView context = topologyApiClient.systemDiagram(systemName);
                systemPreview.setText(formatPreview(context));
                manifestYaml.setValue(buildManifestTemplate(context));
            } catch (Exception ex) {
                systemPreview.setText("Failed to load system context: " + ex.getMessage());
                manifestYaml.clear();
            }
            updateGenerateEnabledState(generate, systemSelector, manifestYaml);
        });

        manifestYaml.addValueChangeListener(event -> updateGenerateEnabledState(generate, systemSelector, manifestYaml));

        add(systemSelector, systemPreview, manifestYaml, generate, metadataPanel, download);
    }

    private void updateGenerateEnabledState(Button generate, ComboBox<String> systemSelector, TextArea manifestYaml) {
        generate.setEnabled(systemSelector.getValue() != null
                && !systemSelector.getValue().isBlank()
                && !manifestYaml.getValue().isBlank());
    }

    private String formatPreview(TopologyApiClient.SystemDiagramView context) {
        return "System: " + context.systemName() + "\n"
                + "Components: " + String.join(", ", context.components()) + "\n"
                + "Target nodes: " + String.join(", ", context.targetNodes());
    }

    private String buildManifestTemplate(TopologyApiClient.SystemDiagramView context) {
        StringBuilder builder = new StringBuilder();
        builder.append("subnets: []\n");
        builder.append("clusters: []\n");
        builder.append("environments: []\n");
        builder.append("systems:\n");
        builder.append("  - name: ").append(context.systemName()).append("\n");
        builder.append("    components:\n");

        List<String> nodes = context.targetNodes();
        for (String component : context.components()) {
            builder.append("      - name: ").append(component).append("\n");
            builder.append("        version: \"0.0.1\"\n");
            if (nodes.isEmpty()) {
                builder.append("        deployments: []\n");
                continue;
            }
            builder.append("        deployments:\n");
            for (String node : nodes) {
                builder.append("          - environment: \"<env-name>\"\n");
                builder.append("            hostname: ").append(node).append("\n");
                builder.append("            cluster: \"\"\n");
                builder.append("            namespace: \"\"\n");
            }
        }
        builder.append("links: []\n");
        return builder.toString();
    }
}
