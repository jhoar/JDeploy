package com.jdeploy.api;

import com.jdeploy.api.dto.TopologyUpdateDtos;
import com.jdeploy.domain.ExecutionEnvironment;
import com.jdeploy.domain.HardwareNode;
import com.jdeploy.security.ApiRoles;
import com.jdeploy.service.TopologyMutationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/api/topology")
@Tag(name = "Topology")
public class TopologyController {
    private final Neo4jClient neo4jClient;
    private final TopologyMutationService mutationService;

    public TopologyController(Neo4jClient neo4jClient, TopologyMutationService mutationService) {
        this.neo4jClient = neo4jClient;
        this.mutationService = mutationService;
    }

    @GetMapping("/systems")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.READ_ONLY + "','" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public List<SystemView> systems() {
        return List.copyOf(neo4jClient.query("""
                MATCH (s:SoftwareSystem)
                OPTIONAL MATCH (s)-[:HAS_COMPONENT]->(c:SoftwareComponent)
                RETURN s.name AS name, count(DISTINCT c) AS componentCount
                ORDER BY name
                """).fetchAs(SystemView.class).mappedBy((t, r) -> new SystemView(r.get("name").asString(), r.get("componentCount").asInt())).all());
    }

    @GetMapping("/hardware-nodes")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.READ_ONLY + "','" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public List<NodeView> hardwareNodes() {
        return List.copyOf(neo4jClient.query("""
                MATCH (n:HardwareNode)
                OPTIONAL MATCH (s:Subnet)-[:CONTAINS_NODE]->(n)
                RETURN n.hostname as hostname, n.ipAddress as ipAddress, n.type as type, s.cidr as subnetCidr
                ORDER BY hostname
                """).fetchAs(NodeView.class).mappedBy((t, r) -> new NodeView(
                r.get("hostname").asString(), r.get("ipAddress").asString(), r.get("type").asString(), r.get("subnetCidr").isNull() ? null : r.get("subnetCidr").asString())).all());
    }

    @GetMapping("/subnets")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.READ_ONLY + "','" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public List<SubnetView> subnets() {
        return List.copyOf(neo4jClient.query("""
                MATCH (s:Subnet)
                OPTIONAL MATCH (s)-[:CONTAINS_NODE]->(n:HardwareNode)
                RETURN s.cidr as cidr, s.vlan as vlan, s.routingZone as routingZone, count(DISTINCT n) as nodeCount
                ORDER BY cidr
                """).fetchAs(SubnetView.class).mappedBy((t, r) -> new SubnetView(r.get("cidr").asString(), r.get("vlan").asString(), r.get("routingZone").asString(), r.get("nodeCount").asInt())).all());
    }

    @GetMapping("/environments")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.READ_ONLY + "','" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public List<EnvironmentView> environments() {
        return List.copyOf(neo4jClient.query("MATCH (e:ExecutionEnvironment) RETURN e.name as name, e.type as type ORDER BY name")
                .fetchAs(EnvironmentView.class)
                .mappedBy((t, r) -> new EnvironmentView(r.get("name").asString(), r.get("type").asString()))
                .all());
    }

    @GetMapping("/systems/{name}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.READ_ONLY + "','" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public TopologyUpdateDtos.SoftwareSystemUpdateRequest system(@PathVariable String name) {
        return neo4jClient.query("MATCH (s:SoftwareSystem {name: $name}) RETURN s.name as name")
                .bind(name).to("name")
                .fetchAs(TopologyUpdateDtos.SoftwareSystemUpdateRequest.class)
                .mappedBy((t, r) -> new TopologyUpdateDtos.SoftwareSystemUpdateRequest(r.get("name").asString()))
                .one().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SoftwareSystem not found"));
    }

    @GetMapping("/nodes/{hostname}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.READ_ONLY + "','" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public TopologyUpdateDtos.HardwareNodeUpdateRequest node(@PathVariable String hostname) {
        return neo4jClient.query("MATCH (n:HardwareNode {hostname: $hostname}) RETURN n.type as type, n.hostname as hostname, n.ipAddress as ipAddress, n.roles as roles")
                .bind(hostname).to("hostname")
                .fetchAs(TopologyUpdateDtos.HardwareNodeUpdateRequest.class)
                .mappedBy((t, r) -> new TopologyUpdateDtos.HardwareNodeUpdateRequest(
                        HardwareNode.NodeType.valueOf(r.get("type").asString()),
                        r.get("hostname").asString(),
                        r.get("ipAddress").asString(),
                        new HashSet<>(r.get("roles").asList(v -> v.asString()))
                ))
                .one().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "HardwareNode not found"));
    }

    @GetMapping("/subnets/{cidr}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.READ_ONLY + "','" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public TopologyUpdateDtos.SubnetUpdateRequest subnet(@PathVariable String cidr) {
        return neo4jClient.query("MATCH (s:Subnet {cidr: $cidr}) RETURN s.cidr as cidr, s.vlan as vlan, s.routingZone as routingZone")
                .bind(cidr).to("cidr")
                .fetchAs(TopologyUpdateDtos.SubnetUpdateRequest.class)
                .mappedBy((t, r) -> new TopologyUpdateDtos.SubnetUpdateRequest(r.get("cidr").asString(), r.get("vlan").asString(), r.get("routingZone").asString()))
                .one().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subnet not found"));
    }

    @GetMapping("/environments/{name}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.READ_ONLY + "','" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public TopologyUpdateDtos.ExecutionEnvironmentUpdateRequest environment(@PathVariable String name) {
        return neo4jClient.query("MATCH (e:ExecutionEnvironment {name: $name}) RETURN e.name as name, e.type as type")
                .bind(name).to("name")
                .fetchAs(TopologyUpdateDtos.ExecutionEnvironmentUpdateRequest.class)
                .mappedBy((t, r) -> new TopologyUpdateDtos.ExecutionEnvironmentUpdateRequest(r.get("name").asString(), ExecutionEnvironment.EnvironmentType.valueOf(r.get("type").asString())))
                .one().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ExecutionEnvironment not found"));
    }

    @PutMapping("/systems/{name}")
    @Operation(summary = "Update software system")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public void updateSystem(@PathVariable String name, @RequestBody @Valid TopologyUpdateDtos.SoftwareSystemUpdateRequest request) { mutationService.updateSoftwareSystem(name, request); }

    @PatchMapping("/systems/{name}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public void patchSystem(@PathVariable String name, @RequestBody @Valid TopologyUpdateDtos.SoftwareSystemUpdateRequest request) { mutationService.updateSoftwareSystem(name, request); }

    @PutMapping("/components/{name}/{version}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public void updateComponent(@PathVariable String name, @PathVariable String version, @RequestBody @Valid TopologyUpdateDtos.SoftwareComponentUpdateRequest request) { mutationService.updateSoftwareComponent(name, version, request); }

    @PatchMapping("/components/{name}/{version}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public void patchComponent(@PathVariable String name, @PathVariable String version, @RequestBody @Valid TopologyUpdateDtos.SoftwareComponentUpdateRequest request) { mutationService.updateSoftwareComponent(name, version, request); }

    @PutMapping("/nodes/{hostname}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public void updateNode(@PathVariable String hostname, @RequestBody @Valid TopologyUpdateDtos.HardwareNodeUpdateRequest request) { mutationService.updateHardwareNode(hostname, request); }

    @PatchMapping("/nodes/{hostname}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public void patchNode(@PathVariable String hostname, @RequestBody @Valid TopologyUpdateDtos.HardwareNodeUpdateRequest request) { mutationService.updateHardwareNode(hostname, request); }

    @PutMapping("/subnets/{cidr}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public void updateSubnet(@PathVariable String cidr, @RequestBody @Valid TopologyUpdateDtos.SubnetUpdateRequest request) { mutationService.updateSubnet(cidr, request); }

    @PatchMapping("/subnets/{cidr}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public void patchSubnet(@PathVariable String cidr, @RequestBody @Valid TopologyUpdateDtos.SubnetUpdateRequest request) { mutationService.updateSubnet(cidr, request); }

    @PutMapping("/environments/{name}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public void updateEnvironment(@PathVariable String name, @RequestBody @Valid TopologyUpdateDtos.ExecutionEnvironmentUpdateRequest request) { mutationService.updateExecutionEnvironment(name, request); }

    @PatchMapping("/environments/{name}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public void patchEnvironment(@PathVariable String name, @RequestBody @Valid TopologyUpdateDtos.ExecutionEnvironmentUpdateRequest request) { mutationService.updateExecutionEnvironment(name, request); }

    @PutMapping("/deployments/{deploymentKey}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public void updateDeployment(@PathVariable String deploymentKey, @RequestBody @Valid TopologyUpdateDtos.DeploymentInstanceUpdateRequest request) { mutationService.updateDeploymentInstance(deploymentKey, request); }

    @PatchMapping("/deployments/{deploymentKey}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public void patchDeployment(@PathVariable String deploymentKey, @RequestBody @Valid TopologyUpdateDtos.DeploymentInstanceUpdateRequest request) { mutationService.updateDeploymentInstance(deploymentKey, request); }

    public record SystemView(String name, Integer componentCount) {}
    public record NodeView(String hostname, String ipAddress, String type, String subnetCidr) {}
    public record SubnetView(String cidr, String vlan, String routingZone, Integer nodeCount) {}
    public record EnvironmentView(String name, String type) {}
}
