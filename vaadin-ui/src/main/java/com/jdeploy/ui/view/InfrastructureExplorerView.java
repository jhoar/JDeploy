package com.jdeploy.ui.view;

import com.jdeploy.ui.client.TopologyApiClient;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.List;

@Route(value = "infrastructure", layout = MainLayout.class)
@PageTitle("Infrastructure Explorer")
@PermitAll
public class InfrastructureExplorerView extends VerticalLayout {

    public InfrastructureExplorerView(TopologyApiClient topologyApiClient) {
        add(new H3("Infrastructure Explorer"));

        TextField subnetFilter = new TextField("Subnet filter");
        TextField systemFilter = new TextField("System filter");
        TextField nodeTypeFilter = new TextField("Node type filter");

        Grid<TopologyApiClient.SoftwareSystemView> systems = new Grid<>(TopologyApiClient.SoftwareSystemView.class, false);
        systems.addColumn(TopologyApiClient.SoftwareSystemView::name).setHeader("System");
        systems.addColumn(TopologyApiClient.SoftwareSystemView::componentCount).setHeader("Components");
        systems.addItemClickListener(e -> getUI().ifPresent(ui -> ui.navigate(SystemDetailView.class, e.getItem().name())));

        Grid<TopologyApiClient.HardwareNodeView> nodes = new Grid<>(TopologyApiClient.HardwareNodeView.class, false);
        nodes.addColumn(TopologyApiClient.HardwareNodeView::hostname).setHeader("Hostname");
        nodes.addColumn(TopologyApiClient.HardwareNodeView::type).setHeader("Type");
        nodes.addColumn(TopologyApiClient.HardwareNodeView::ipAddress).setHeader("IP");
        nodes.addColumn(TopologyApiClient.HardwareNodeView::subnetCidr).setHeader("Subnet");
        nodes.addItemClickListener(e -> getUI().ifPresent(ui -> ui.navigate(NodeDetailView.class, e.getItem().hostname())));

        Grid<TopologyApiClient.SubnetView> subnets = new Grid<>(TopologyApiClient.SubnetView.class, false);
        subnets.addColumn(TopologyApiClient.SubnetView::cidr).setHeader("CIDR");
        subnets.addColumn(TopologyApiClient.SubnetView::vlan).setHeader("VLAN");
        subnets.addColumn(TopologyApiClient.SubnetView::routingZone).setHeader("Zone");
        subnets.addColumn(TopologyApiClient.SubnetView::nodeCount).setHeader("Nodes");
        subnets.addItemClickListener(e -> getUI().ifPresent(ui -> ui.navigate(SubnetDetailView.class, e.getItem().cidr())));

        Grid<TopologyApiClient.ExecutionEnvironmentView> environments = new Grid<>(TopologyApiClient.ExecutionEnvironmentView.class, false);
        environments.addColumn(TopologyApiClient.ExecutionEnvironmentView::name).setHeader("Environment");
        environments.addColumn(TopologyApiClient.ExecutionEnvironmentView::type).setHeader("Type");
        environments.addItemClickListener(e -> getUI().ifPresent(ui -> ui.navigate(EnvironmentDetailView.class, e.getItem().name())));

        Tabs tabs = new Tabs(new Tab("Software Systems"), new Tab("Hardware Nodes"), new Tab("Subnets"), new Tab("Execution Environments"));
        VerticalLayout content = new VerticalLayout(systems);

        tabs.addSelectedChangeListener(event -> {
            content.removeAll();
            if (tabs.getSelectedIndex() == 0) {
                content.add(systems);
            } else if (tabs.getSelectedIndex() == 1) {
                content.add(nodes);
            } else if (tabs.getSelectedIndex() == 2) {
                content.add(subnets);
            } else {
                content.add(environments);
            }
        });

        Button refresh = new Button("Apply filters", e -> {
            try {
                List<TopologyApiClient.SoftwareSystemView> systemData = topologyApiClient.systems().stream()
                        .filter(it -> it.name() != null && it.name().toLowerCase().contains(systemFilter.getValue().toLowerCase()))
                        .toList();
                systems.setItems(systemData);

                List<TopologyApiClient.HardwareNodeView> nodeData = topologyApiClient.hardwareNodes().stream()
                        .filter(it -> (it.subnetCidr() != null && it.subnetCidr().toLowerCase().contains(subnetFilter.getValue().toLowerCase()))
                                && (it.type() != null && it.type().toLowerCase().contains(nodeTypeFilter.getValue().toLowerCase())))
                        .toList();
                nodes.setItems(nodeData);

                subnets.setItems(topologyApiClient.subnets().stream()
                        .filter(it -> it.cidr() != null && it.cidr().toLowerCase().contains(subnetFilter.getValue().toLowerCase()))
                        .toList());
                environments.setItems(topologyApiClient.environments());
            } catch (Exception ex) {
                Notification.show("Failed to load infrastructure data: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });

        add(new HorizontalLayout(subnetFilter, systemFilter, nodeTypeFilter, refresh), tabs, content);
        refresh.click();
    }
}
