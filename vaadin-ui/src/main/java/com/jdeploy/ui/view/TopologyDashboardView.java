package com.jdeploy.ui.view;

import com.jdeploy.ui.client.TopologyApiClient;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "topology", layout = MainLayout.class)
@PageTitle("Topology Dashboard")
@PermitAll
public class TopologyDashboardView extends VerticalLayout {

    public TopologyDashboardView(TopologyApiClient topologyApiClient) {
        add(new H3("Topology Dashboard"));
        add(new Text("Use this dashboard to ingest manifests, browse infrastructure, and generate diagrams."));

        HorizontalLayout actions = new HorizontalLayout(
                new Button("Open Manifest Ingest", e -> getUI().ifPresent(ui -> ui.navigate(ManifestIngestView.class))),
                new Button("Open Infrastructure Explorer", e -> getUI().ifPresent(ui -> ui.navigate(InfrastructureExplorerView.class))),
                new Button("Open Diagram View", e -> getUI().ifPresent(ui -> ui.navigate(DiagramView.class))),
                new Button("Refresh Topology Snapshot", e -> {
                    try {
                        int systems = topologyApiClient.systems().size();
                        int nodes = topologyApiClient.hardwareNodes().size();
                        int subnets = topologyApiClient.subnets().size();
                        Notification.show("Systems: " + systems + ", Nodes: " + nodes + ", Subnets: " + subnets);
                    } catch (Exception ex) {
                        Notification.show("Unable to fetch topology snapshot: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
                    }
                })
        );
        add(actions);
    }
}
