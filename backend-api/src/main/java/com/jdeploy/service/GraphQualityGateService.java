package com.jdeploy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class GraphQualityGateService {

    private static final Logger log = LoggerFactory.getLogger(GraphQualityGateService.class);

    private final Neo4jClient neo4jClient;
    private final boolean scheduledReportingEnabled;
    private final AtomicReference<QualityGateSnapshot> latestSnapshot = new AtomicReference<>();

    public GraphQualityGateService(Neo4jClient neo4jClient,
                                   @Value("${jdeploy.quality-reporting.enabled:true}") boolean scheduledReportingEnabled) {
        this.neo4jClient = neo4jClient;
        this.scheduledReportingEnabled = scheduledReportingEnabled;
    }

    public QualityGateReport evaluateGraph() {
        List<String> orphanDeployments = neo4jClient.query("""
                MATCH (d:DeploymentInstance)
                WHERE NOT (d)-[:TARGETS]->(:HardwareNode)
                   OR NOT (d)-[:TARGETS]->(:ExecutionEnvironment)
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
                WHERE NOT (d)-[:TARGETS]->(:ExecutionEnvironment)
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

    @Scheduled(cron = "${jdeploy.quality-reporting.cron:0 */15 * * * *}")
    public void runScheduledQualityReport() {
        if (!scheduledReportingEnabled) {
            return;
        }

        QualityGateSnapshot snapshot = evaluateAndStore();
        int findingCount = snapshot.report().findings().values().stream().mapToInt(List::size).sum();

        if (snapshot.report().passed()) {
            log.info("Graph quality report passed with no findings at {}", snapshot.generatedAt());
        } else {
            log.warn("Graph quality report found {} issue(s) at {}: {}", findingCount, snapshot.generatedAt(), snapshot.report().findings());
        }
    }

    public QualityGateSnapshot latestReport() {
        QualityGateSnapshot cached = latestSnapshot.get();
        return cached != null ? cached : evaluateAndStore();
    }

    private QualityGateSnapshot evaluateAndStore() {
        QualityGateSnapshot snapshot = new QualityGateSnapshot(Instant.now(), evaluateGraph());
        latestSnapshot.set(snapshot);
        return snapshot;
    }

    public record QualityGateReport(Map<String, List<String>> findings) {
        public boolean passed() {
            return findings.values().stream().allMatch(List::isEmpty);
        }
    }

    public record QualityGateSnapshot(Instant generatedAt, QualityGateReport report) {
    }
}
