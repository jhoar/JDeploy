package com.jdeploy.ui.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class TopologyApiClient {

    private final RestClient restClient;

    public TopologyApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<SoftwareSystemView> systems() {
        return restClient.get().uri("/api/topology/systems")
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    public List<HardwareNodeView> hardwareNodes() {
        return restClient.get().uri("/api/topology/hardware-nodes")
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    public List<SubnetView> subnets() {
        return restClient.get().uri("/api/topology/subnets")
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    public List<ExecutionEnvironmentView> environments() {
        return restClient.get().uri("/api/topology/environments")
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    public SystemDiagramView systemDiagram(String systemName) {
        return restClient.get()
                .uri("/api/diagrams/system/{systemId}", systemName)
                .retrieve()
                .body(SystemDiagramView.class);
    }

    public record SoftwareSystemView(String name, Integer componentCount) {
    }

    public record HardwareNodeView(String hostname, String ipAddress, String type, String subnetCidr) {
    }

    public record SubnetView(String cidr, String vlan, String routingZone, Integer nodeCount) {
    }

    public record ExecutionEnvironmentView(String name, String type) {
    }

    public record SystemDiagramView(String systemName, List<String> components, List<String> targetNodes) {
    }
}
