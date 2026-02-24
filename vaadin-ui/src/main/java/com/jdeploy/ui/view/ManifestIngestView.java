package com.jdeploy.ui.view;

import com.jdeploy.security.ApiRoles;
import com.jdeploy.ui.client.ManifestApiClient;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Route(value = "manifests/ingest", layout = MainLayout.class)
@PageTitle("Manifest Ingest")
@PermitAll
public class ManifestIngestView extends VerticalLayout {

    public ManifestIngestView(ManifestApiClient manifestApiClient) {
        add(new H3("Manifest Ingest"));

        TextArea yamlInput = new TextArea("Paste manifest YAML");
        yamlInput.setWidthFull();
        yamlInput.setMinHeight("260px");

        MemoryBuffer memoryBuffer = new MemoryBuffer();
        Upload upload = new Upload(memoryBuffer);
        upload.setAcceptedFileTypes(".yaml", ".yml", "text/yaml");
        upload.addSucceededListener(event -> {
            try {
                String content = new String(memoryBuffer.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                yamlInput.setValue(content);
            } catch (IOException e) {
                Notification.show("Failed reading file: " + e.getMessage());
            }
        });

        Pre resultPanel = new Pre("Submit a manifest to see ingest details.");
        Button submit = new Button("Submit", event -> {
            try {
                if (yamlInput.getValue().isBlank()) {
                    Notification.show("Provide a YAML file or paste manifest text first.");
                    return;
                }
                ManifestApiClient.ManifestIngestResult result = manifestApiClient.ingest(yamlInput.getValue());
                resultPanel.setText("status: " + result.status() + "\ncreated: " + safe(result.created()) +
                        "\nupdated: " + safe(result.updated()) + "\nerrors: " + safe(result.errors()));
            } catch (AccessDeniedException denied) {
                resultPanel.setText("status: FAILED\ncreated: n/a\nupdated: n/a\nerrors: Access denied for role " + ApiRoles.TOPOLOGY_INGEST);
            } catch (Exception ex) {
                resultPanel.setText("status: FAILED\ncreated: n/a\nupdated: n/a\nerrors: " + ex.getMessage());
            }
        });

        add(upload, yamlInput, submit, resultPanel);
    }

    private String safe(Object value) {
        return value == null ? "n/a" : value.toString();
    }
}
