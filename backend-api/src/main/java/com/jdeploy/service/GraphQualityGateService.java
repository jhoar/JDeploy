package com.jdeploy.service;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class GraphQualityGateService {

    private final Neo4jClient neo4jClient;

    public GraphQualityGateService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public QualityGateReport evaluateGraph() {
        List<String> orphanDeployments = neo4jClient.query("""
                MATCH (d:DeploymentInstance)
                WHERE NOT (d)-[:TARGET_NODE]->(:HardwareNode)
                   OR NOT (d)-[:TARGET_ENVIRONMENT]->(:ExecutionEnvironment)
                RETURN d.deploymentKey AS value
                ORDER BY value
                """)
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("value").asString())
                .all();

        List<String> nodesWithoutSubnet = neo4jClient.query("""
                MATCH (n:HardwareNode)
                WHERE NOT (:Subnet)-[:CONTAINS_NODE]->(n)
                RETURN n.hostname AS value
                ORDER BY value
                """)
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("value").asString())
                .all();

        List<String> duplicateHostnames = neo4jClient.query("""
                MATCH (n:HardwareNode)
                WITH n.hostname AS hostname, count(*) AS c
                WHERE c > 1
                RETURN hostname + ' (' + toString(c) + ')' AS value
                ORDER BY value
                """)
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("value").asString())
                .all();

        List<String> duplicateIps = neo4jClient.query("""
                MATCH (n:HardwareNode)
                WHERE n.ipAddress IS NOT NULL
                WITH n.ipAddress AS ipAddress, count(*) AS c
                WHERE c > 1
                RETURN ipAddress + ' (' + toString(c) + ')' AS value
                ORDER BY value
                """)
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("value").asString())
                .all();

        List<String> softwareLinkedToMissingEnvironment = neo4jClient.query("""
                MATCH (c:SoftwareComponent)-[:HAS_DEPLOYMENT]->(d:DeploymentInstance)
                WHERE NOT (d)-[:TARGET_ENVIRONMENT]->(:ExecutionEnvironment)
                RETURN c.name + ':' + c.version + ' -> ' + coalesce(d.deploymentKey, 'unknown') AS value
                ORDER BY value
                """)
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("value").asString())
                .all();

        return new QualityGateReport(Map.of(
                "orphanDeployments", orphanDeployments,
                "nodesWithoutSubnet", nodesWithoutSubnet,
                "duplicateHostnames", duplicateHostnames,
                "duplicateIps", duplicateIps,
                "softwareLinkedToMissingEnvironment", softwareLinkedToMissingEnvironment));
    }

    public record QualityGateReport(Map<String, List<String>> findings) {
        public boolean passed() {
            return findings.values().stream().allMatch(List::isEmpty);
        }
    }
}
