package com.jdeploy.ui.client;

import com.jdeploy.domain.ExecutionEnvironment;
import com.jdeploy.domain.HardwareNode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;

@Service
public class TopologyApiClient {

    private final RestClient restClient;

    public TopologyApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<SoftwareSystemView> systems() {
        return restClient.get().uri("/api/topology/systems").retrieve().body(new ParameterizedTypeReference<>() {});
    }

    public List<HardwareNodeView> hardwareNodes() {
        return restClient.get().uri("/api/topology/hardware-nodes").retrieve().body(new ParameterizedTypeReference<>() {});
    }

    public List<SubnetView> subnets() {
        return restClient.get().uri("/api/topology/subnets").retrieve().body(new ParameterizedTypeReference<>() {});
    }

    public List<ExecutionEnvironmentView> environments() {
        return restClient.get().uri("/api/topology/environments").retrieve().body(new ParameterizedTypeReference<>() {});
    }

    public SoftwareSystemUpdateRequest system(String name) {
        return restClient.get().uri("/api/topology/systems/{name}", name).retrieve().body(SoftwareSystemUpdateRequest.class);
    }

    public HardwareNodeUpdateRequest node(String hostname) {
        return restClient.get().uri("/api/topology/nodes/{hostname}", hostname).retrieve().body(HardwareNodeUpdateRequest.class);
    }

    public SubnetUpdateRequest subnet(String cidr) {
        return restClient.get().uri("/api/topology/subnets/{cidr}", cidr).retrieve().body(SubnetUpdateRequest.class);
    }

    public ExecutionEnvironmentUpdateRequest environment(String name) {
        return restClient.get().uri("/api/topology/environments/{name}", name).retrieve().body(ExecutionEnvironmentUpdateRequest.class);
    }

    public void updateSystem(String existingName, SoftwareSystemUpdateRequest request) {
        restClient.put().uri("/api/topology/systems/{name}", existingName).body(request).retrieve().toBodilessEntity();
    }

    public void updateNode(String existingHostname, HardwareNodeUpdateRequest request) {
        restClient.put().uri("/api/topology/nodes/{hostname}", existingHostname).body(request).retrieve().toBodilessEntity();
    }

    public void updateSubnet(String existingCidr, SubnetUpdateRequest request) {
        restClient.put().uri("/api/topology/subnets/{cidr}", existingCidr).body(request).retrieve().toBodilessEntity();
    }

    public void updateEnvironment(String existingName, ExecutionEnvironmentUpdateRequest request) {
        restClient.put().uri("/api/topology/environments/{name}", existingName).body(request).retrieve().toBodilessEntity();
    }

    public SystemDiagramView systemDiagram(String systemName) {
        return restClient.get().uri("/api/diagrams/system/{systemId}", systemName).retrieve().body(SystemDiagramView.class);
    }

    public record SoftwareSystemView(String name, Integer componentCount) {}
    public record HardwareNodeView(String hostname, String ipAddress, String type, String subnetCidr) {}
    public record SubnetView(String cidr, String vlan, String routingZone, Integer nodeCount) {}
    public record ExecutionEnvironmentView(String name, String type) {}
    public record SystemDiagramView(String systemName, List<String> components, List<String> targetNodes) {}

    public record SoftwareSystemUpdateRequest(String name) {}
    public record HardwareNodeUpdateRequest(HardwareNode.NodeType type, String hostname, String ipAddress, Set<String> roles) {}
    public record SubnetUpdateRequest(String cidr, String vlan, String routingZone) {}
    public record ExecutionEnvironmentUpdateRequest(String name, ExecutionEnvironment.EnvironmentType type) {}
}
