package com.jdeploy.ui.view;

import com.jdeploy.artifact.ArtifactMetadata;
import com.jdeploy.ui.client.ArtifactApiClient;
import com.jdeploy.ui.client.TopologyApiClient;
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
import jakarta.annotation.security.PermitAll;

@Route(value = "diagram", layout = MainLayout.class)
@PageTitle("Diagram")
@PermitAll
public class DiagramView extends VerticalLayout {

    public DiagramView(TopologyApiClient topologyApiClient, ArtifactApiClient artifactApiClient) {
        add(new H3("Diagram Generator"));

        ComboBox<String> systemSelector = new ComboBox<>("System");
        systemSelector.setItems(topologyApiClient.systems().stream().map(TopologyApiClient.SoftwareSystemView::name).toList());

        TextArea manifestYaml = new TextArea("Manifest YAML for generation");
        manifestYaml.setWidthFull();
        manifestYaml.setMinHeight("240px");

        Pre metadataPanel = new Pre("No artifact generated yet.");
        Anchor download = new Anchor();
        download.setText("Download artifact");
        download.setVisible(false);

        Button generate = new Button("Generate", e -> {
            try {
                if (manifestYaml.getValue().isBlank()) {
                    Notification.show("Provide manifest YAML.");
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

        add(systemSelector, manifestYaml, generate, metadataPanel, download);
    }
}
