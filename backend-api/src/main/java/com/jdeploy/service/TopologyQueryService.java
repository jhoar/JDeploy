package com.jdeploy.service;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
public class TopologyQueryService {

    private final Neo4jClient neo4jClient;

    public TopologyQueryService(Neo4jClient neo4jClient) {
        this.neo4jClient = Objects.requireNonNull(neo4jClient, "neo4jClient must not be null");
    }

    public List<DeploymentView> deploymentsBySubnet(String subnetId) {
        if (subnetId == null || subnetId.isBlank()) {
            throw new PreconditionViolationException("subnetId is required");
        }

        return List.copyOf(neo4jClient.query("""
                MATCH (s:Subnet {cidr: $subnetId})-[:CONTAINS_NODE]->(n:HardwareNode)
                MATCH (d:DeploymentInstance)-[:TARGETS]->(n)
                RETURN n.hostname as hostname, d.deploymentKey as deploymentKey
                ORDER BY hostname
                """)
                .bind(subnetId).to("subnetId")
                .fetchAs(DeploymentView.class)
                .mappedBy((typeSystem, record) -> new DeploymentView(record.get("hostname").asString(), record.get("deploymentKey").asString()))
                .all());
    }

    public List<ImpactView> impactByNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new PreconditionViolationException("nodeId is required");
        }

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
                        toStringList(row.get("peerNodes")),
                        toStringList(row.get("sourceClusters")),
                        toStringList(row.get("peerClusters"))))
                .toList();
    }

    public SystemDiagramView systemDiagram(String systemId) {
        if (systemId == null || systemId.isBlank()) {
            throw new PreconditionViolationException("systemId is required");
        }

        List<String> components = List.copyOf(neo4jClient.query("""
                MATCH (s:SoftwareSystem {name: $systemId})-[:HAS_COMPONENT]->(c:SoftwareComponent)
                RETURN c.name + ':' + c.version as component
                ORDER BY component
                """)
                .bind(systemId).to("systemId")
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("component").asString())
                .all());

        List<String> nodes = List.copyOf(neo4jClient.query("""
                MATCH (s:SoftwareSystem {name: $systemId})-[:HAS_COMPONENT]->(c:SoftwareComponent)-[:HAS_DEPLOYMENT]->(d:DeploymentInstance)-[:TARGETS]->(n:HardwareNode)
                RETURN DISTINCT n.hostname as hostname
                ORDER BY hostname
                """)
                .bind(systemId).to("systemId")
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("hostname").asString())
                .all());

        return new SystemDiagramView(systemId, components, nodes);
    }


    private static List<String> toStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public record DeploymentView(String hostname, String deploymentKey) {
    }

    public record ImpactView(String componentName,
                             String deploymentKey,
                             List<String> peerNodes,
                             List<String> sourceClusters,
                             List<String> peerClusters) {
    }

    public record SystemDiagramView(String systemName, List<String> components, List<String> targetNodes) {
    }
}
