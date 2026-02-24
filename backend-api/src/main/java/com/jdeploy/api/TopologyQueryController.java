package com.jdeploy.api;

import com.jdeploy.security.ApiRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@SecurityRequirement(name = "basicAuth")
public class TopologyQueryController {

    private final Neo4jClient neo4jClient;

    public TopologyQueryController(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @GetMapping("/deployments/subnet/{subnetId}")
    @PreAuthorize("hasAuthority('" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "List deployments in a subnet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deployments found", content = @Content(array = @ArraySchema(schema = @Schema(implementation = DeploymentView.class)))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    public List<DeploymentView> deploymentsBySubnet(@PathVariable String subnetId) {
        return neo4jClient.query("""
                MATCH (s:Subnet {cidr: $subnetId})-[:CONTAINS_NODE]->(n:HardwareNode)
                MATCH (d:DeploymentInstance)-[:TARGETS]->(n)
                RETURN n.hostname as hostname, d.deploymentKey as deploymentKey
                ORDER BY hostname
                """)
                .bind(subnetId).to("subnetId")
                .fetchAs(DeploymentView.class)
                .mappedBy((typeSystem, record) -> new DeploymentView(record.get("hostname").asString(), record.get("deploymentKey").asString()))
                .all();
    }

    @GetMapping("/subnets/{subnetId}/deployments")
    @PreAuthorize("hasAuthority('" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "Show deployments in subnet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deployments found", content = @Content(array = @ArraySchema(schema = @Schema(implementation = DeploymentView.class)))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    public List<DeploymentView> deploymentsInSubnet(@PathVariable String subnetId) {
        return deploymentsBySubnet(subnetId);
    }

    @GetMapping("/impact/node/{nodeId}")
    @PreAuthorize("hasAuthority('" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "Show dependency impact for a node")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Impact records returned", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ImpactView.class)))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    public List<ImpactView> impactByNode(@PathVariable String nodeId) {
        return neo4jClient.query("""
                MATCH (n:HardwareNode {hostname: $nodeId})<-[:TARGETS]-(d:DeploymentInstance)<-[:HAS_DEPLOYMENT]-(c:SoftwareComponent)
                OPTIONAL MATCH (sourceCluster)-[:HAS_NODE]->(n)
                OPTIONAL MATCH (l:NetworkLink)-[:CONNECTS_FROM]->(n)
                OPTIONAL MATCH (l)-[:CONNECTS_TO]->(peer:HardwareNode)
                OPTIONAL MATCH (peerCluster)-[:HAS_NODE]->(peer)
                RETURN c.name as componentName,
                       d.deploymentKey as deploymentKey,
                       collect(DISTINCT peer.hostname) as peerNodes,
                       collect(DISTINCT labels(sourceCluster)[0] + ':' + sourceCluster.name) as sourceClusters,
                       collect(DISTINCT labels(peerCluster)[0] + ':' + peerCluster.name) as peerClusters
                """)
                .bind(nodeId).to("nodeId")
                .fetch()
                .all()
                .stream()
                .map(row -> new ImpactView(
                        String.valueOf(row.get("componentName")),
                        String.valueOf(row.get("deploymentKey")),
                        (List<String>) row.getOrDefault("peerNodes", List.of()),
                        (List<String>) row.getOrDefault("sourceClusters", List.of()),
                        (List<String>) row.getOrDefault("peerClusters", List.of())))
                .toList();
    }

    @GetMapping("/clusters/{clusterName}/nodes")
    @PreAuthorize("hasAuthority('" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "List all nodes in a cluster")
    public List<ClusterNodeView> nodesInCluster(@PathVariable String clusterName) {
        return neo4jClient.query("""
                MATCH (c)-[:HAS_NODE]->(n:HardwareNode)
                WHERE c.name = $clusterName
                  AND ('GridCluster' IN labels(c) OR 'KubernetesCluster' IN labels(c))
                RETURN labels(c)[0] as clusterType, c.name as clusterName, n.hostname as hostname, n.ipAddress as ipAddress
                ORDER BY hostname
                """)
                .bind(clusterName).to("clusterName")
                .fetchAs(ClusterNodeView.class)
                .mappedBy((typeSystem, record) -> new ClusterNodeView(
                        record.get("clusterType").asString(),
                        record.get("clusterName").asString(),
                        record.get("hostname").asString(),
                        record.get("ipAddress").asString()))
                .all();
    }

    @GetMapping("/clusters/{clusterName}/subnets/{subnetId}/nodes")
    @PreAuthorize("hasAuthority('" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "List cluster nodes scoped to a subnet")
    public List<ClusterNodeView> nodesInClusterAndSubnet(@PathVariable String clusterName, @PathVariable String subnetId) {
        return neo4jClient.query("""
                MATCH (c)-[:HAS_NODE]->(n:HardwareNode)
                MATCH (:Subnet {cidr: $subnetId})-[:CONTAINS_NODE]->(n)
                WHERE c.name = $clusterName
                  AND ('GridCluster' IN labels(c) OR 'KubernetesCluster' IN labels(c))
                RETURN labels(c)[0] as clusterType, c.name as clusterName, n.hostname as hostname, n.ipAddress as ipAddress
                ORDER BY hostname
                """)
                .bindAll(java.util.Map.of("clusterName", clusterName, "subnetId", subnetId))
                .fetchAs(ClusterNodeView.class)
                .mappedBy((typeSystem, record) -> new ClusterNodeView(
                        record.get("clusterType").asString(),
                        record.get("clusterName").asString(),
                        record.get("hostname").asString(),
                        record.get("ipAddress").asString()))
                .all();
    }

    @GetMapping("/diagrams/system/{systemId}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.ARTIFACT_GENERATE + "','" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "Get system deployment diagram model")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Diagram model returned", content = @Content(schema = @Schema(implementation = SystemDiagramView.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
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
                MATCH (s:SoftwareSystem {name: $systemId})-[:HAS_COMPONENT]->(c:SoftwareComponent)-[:HAS_DEPLOYMENT]->(d:DeploymentInstance)-[:TARGETS]->(n:HardwareNode)
                RETURN DISTINCT n.hostname as hostname
                ORDER BY hostname
                """)
                .bind(systemId).to("systemId")
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("hostname").asString())
                .all();

        return new SystemDiagramView(systemId, components, nodes);
    }

    @GetMapping("/systems/{systemId}/diagram")
    @PreAuthorize("hasAuthority('" + ApiRoles.ARTIFACT_GENERATE + "')")
    @Operation(summary = "Generate diagram for system")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Diagram model returned", content = @Content(schema = @Schema(implementation = SystemDiagramView.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    public SystemDiagramView generateDiagramForSystem(@PathVariable String systemId) {
        return systemDiagram(systemId);
    }

    @GetMapping("/impact/node/{nodeId}/systems")
    @PreAuthorize("hasAuthority('" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "List systems impacted by node failure")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Impacted systems returned", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SystemImpactView.class)))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    public List<SystemImpactView> systemsImpactedByNodeFailure(@PathVariable String nodeId) {
        return neo4jClient.query("""
                MATCH (n:HardwareNode {hostname: $nodeId})<-[:TARGETS]-(d:DeploymentInstance)<-[:HAS_DEPLOYMENT]-(c:SoftwareComponent)
                MATCH (s:SoftwareSystem)-[:HAS_COMPONENT]->(c)
                RETURN s.name as systemName, collect(DISTINCT c.name + ':' + c.version) as impactedComponents
                ORDER BY systemName
                """)
                .bind(nodeId).to("nodeId")
                .fetch()
                .all()
                .stream()
                .map(row -> new SystemImpactView(
                        String.valueOf(row.get("systemName")),
                        (List<String>) row.getOrDefault("impactedComponents", List.of())))
                .toList();
    }

    @Schema(name = "DeploymentView")
    public record DeploymentView(String hostname, String deploymentKey) {
    }

    @Schema(name = "ImpactView")
    public record ImpactView(String componentName,
                             String deploymentKey,
                             List<String> peerNodes,
                             List<String> sourceClusters,
                             List<String> peerClusters) {
    }

    @Schema(name = "ClusterNodeView")
    public record ClusterNodeView(String clusterType, String clusterName, String hostname, String ipAddress) {
    }

    @Schema(name = "SystemDiagramView")
    public record SystemDiagramView(String systemName, List<String> components, List<String> targetNodes) {
    }

    @Schema(name = "SystemImpactView")
    public record SystemImpactView(String systemName, List<String> impactedComponents) {
    }
}
