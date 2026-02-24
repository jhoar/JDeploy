package com.jdeploy.api;

import com.jdeploy.security.ApiRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Topology Queries")
public class TopologyQueryController {

    private final Neo4jClient neo4jClient;

    public TopologyQueryController(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @GetMapping("/deployments/subnet/{subnetId}")
    @PreAuthorize("hasAuthority('" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "List deployments in a subnet")
    @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = DeploymentView.class))))
    public List<DeploymentView> deploymentsBySubnet(@PathVariable String subnetId) {
        return neo4jClient.query("""
                MATCH (s:Subnet {cidr: $subnetId})-[:CONTAINS_NODE]->(n:HardwareNode)
                MATCH (d:DeploymentInstance)-[:TARGET_NODE]->(n)
                RETURN n.hostname as hostname, d.deploymentKey as deploymentKey
                ORDER BY hostname
                """)
                .bind(subnetId).to("subnetId")
                .fetchAs(DeploymentView.class)
                .mappedBy((typeSystem, record) -> new DeploymentView(record.get("hostname").asString(), record.get("deploymentKey").asString()))
                .all();
    }

    @GetMapping("/impact/node/{nodeId}")
    @PreAuthorize("hasAuthority('" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "Show dependency impact for a node")
    @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ImpactView.class))))
    public List<ImpactView> impactByNode(@PathVariable String nodeId) {
        return neo4jClient.query("""
                MATCH (n:HardwareNode {hostname: $nodeId})<-[:TARGET_NODE]-(d:DeploymentInstance)<-[:HAS_DEPLOYMENT]-(c:SoftwareComponent)
                OPTIONAL MATCH (l:NetworkLink)-[:CONNECTS_FROM]->(n)
                OPTIONAL MATCH (l)-[:CONNECTS_TO]->(peer:HardwareNode)
                RETURN c.name as componentName, d.deploymentKey as deploymentKey, collect(DISTINCT peer.hostname) as peerNodes
                """)
                .bind(nodeId).to("nodeId")
                .fetch()
                .all()
                .stream()
                .map(row -> new ImpactView(
                        String.valueOf(row.get("componentName")),
                        String.valueOf(row.get("deploymentKey")),
                        (List<String>) row.getOrDefault("peerNodes", List.of())))
                .toList();
    }

    @GetMapping("/diagrams/system/{systemId}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.ARTIFACT_GENERATE + "','" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "Get system deployment diagram model")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = SystemDiagramView.class)))
    public SystemDiagramView systemDiagram(@PathVariable String systemId) {
        List<String> components = neo4jClient.query("""
                MATCH (s:SoftwareSystem {name: $systemId})-[:HAS_COMPONENT]->(c:SoftwareComponent)
                RETURN c.name + ':' + c.version as component
                ORDER BY component
                """)
                .bind(systemId).to("systemId")
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("component").asString())
                .all();

        List<String> nodes = neo4jClient.query("""
                MATCH (s:SoftwareSystem {name: $systemId})-[:HAS_COMPONENT]->(c:SoftwareComponent)-[:HAS_DEPLOYMENT]->(d:DeploymentInstance)-[:TARGET_NODE]->(n:HardwareNode)
                RETURN DISTINCT n.hostname as hostname
                ORDER BY hostname
                """)
                .bind(systemId).to("systemId")
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("hostname").asString())
                .all();

        return new SystemDiagramView(systemId, components, nodes);
    }

    @Schema(name = "DeploymentView")
    public record DeploymentView(String hostname, String deploymentKey) {
    }

    @Schema(name = "ImpactView")
    public record ImpactView(String componentName, String deploymentKey, List<String> peerNodes) {
    }

    @Schema(name = "SystemDiagramView")
    public record SystemDiagramView(String systemName, List<String> components, List<String> targetNodes) {
    }
}
