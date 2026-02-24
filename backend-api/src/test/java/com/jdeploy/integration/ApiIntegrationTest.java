package com.jdeploy.integration;

import com.jdeploy.api.ManifestController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5")
            .withAdminPassword("changeit");

    @DynamicPropertySource
    static void neo4jProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "changeit");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private Neo4jClient neo4jClient;

    @LocalServerPort
    int port;

    @BeforeEach
    void resetGraph() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    }

    @Test
    void ingestionAndQueryEndpointsWorkWithRbac() {
        String yaml = validManifestYaml();
        TestRestTemplate ingestClient = restTemplate.withBasicAuth("ingest", "ingest-password");

        ResponseEntity<ManifestController.OperationResult> ingestResponse = ingestClient.postForEntity(
                "http://localhost:" + port + "/api/manifests/ingest",
                yaml,
                ManifestController.OperationResult.class);

        assertEquals(HttpStatus.OK, ingestResponse.getStatusCode());

        TestRestTemplate readClient = restTemplate.withBasicAuth("reader", "reader-password");
        ResponseEntity<List<Map<String, Object>>> deploymentsResponse = readClient.exchange(
                "http://localhost:" + port + "/api/deployments/subnet/10.10.0.0/24",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {
                });

        assertEquals(HttpStatus.OK, deploymentsResponse.getStatusCode());
        assertFalse(deploymentsResponse.getBody().isEmpty());

        ResponseEntity<String> forbiddenIngest = readClient.postForEntity(
                "http://localhost:" + port + "/api/manifests/ingest",
                yaml,
                String.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenIngest.getStatusCode());
    }

    @Test
    void graphQualityGateFindsExplicitViolations() {
        neo4jClient.query("CREATE (:DeploymentInstance {deploymentKey:'orphan'})").run();
        neo4jClient.query("CREATE (:HardwareNode {hostname:'nosubnet-01', ipAddress:'10.10.1.10'})").run();
        neo4jClient.query("CREATE (:HardwareNode {hostname:'dup-host', ipAddress:'10.10.1.11'})").run();
        neo4jClient.query("CREATE (:HardwareNode {hostname:'dup-host', ipAddress:'10.10.1.12'})").run();
        neo4jClient.query("CREATE (:HardwareNode {hostname:'dup-ip-01', ipAddress:'10.10.1.99'})").run();
        neo4jClient.query("CREATE (:HardwareNode {hostname:'dup-ip-02', ipAddress:'10.10.1.99'})").run();
        neo4jClient.query("""
                CREATE (c:SoftwareComponent {name:'billing-api', version:'1.0.0'})
                CREATE (d:DeploymentInstance {deploymentKey:'qa@missing-node:billing-api'})
                CREATE (c)-[:HAS_DEPLOYMENT]->(d)
                """).run();

        TestRestTemplate readClient = restTemplate.withBasicAuth("reader", "reader-password");
        ResponseEntity<Map<String, Object>> qualityResponse = readClient.exchange(
                "http://localhost:" + port + "/api/quality-gates/graph",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {
                });

        assertEquals(HttpStatus.OK, qualityResponse.getStatusCode());
        Map<String, Object> body = qualityResponse.getBody();
        Map<String, List<String>> findings = (Map<String, List<String>>) body.get("findings");
        assertFalse(findings.values().stream().allMatch(List::isEmpty));
        assertTrue(findings.get("orphanDeployments").contains("orphan"));
        assertTrue(findings.get("nodesWithoutSubnet").contains("nosubnet-01"));
        assertTrue(findings.get("duplicateHostnames").stream().anyMatch(v -> v.startsWith("dup-host")));
        assertTrue(findings.get("duplicateIps").stream().anyMatch(v -> v.startsWith("10.10.1.99")));
        assertTrue(findings.get("softwareLinkedToMissingEnvironment").stream().anyMatch(v -> v.contains("billing-api")));
    }

    @Test
    void actuatorMetricsExposeCustomCounters() {
        TestRestTemplate ingestClient = restTemplate.withBasicAuth("ingest", "ingest-password");
        ingestClient.postForEntity("http://localhost:" + port + "/api/manifests/ingest", validManifestYaml(), String.class);
        ingestClient.postForEntity("http://localhost:" + port + "/api/quality-gates/manifest", "bad: [", String.class);

        TestRestTemplate readClient = restTemplate.withBasicAuth("reader", "reader-password");
        ResponseEntity<String> prometheus = readClient.getForEntity("http://localhost:" + port + "/actuator/prometheus", String.class);

        assertEquals(HttpStatus.OK, prometheus.getStatusCode());
        assertTrue(prometheus.getBody().contains("jdeploy_ingestion_errors_total"));
    }

    private String validManifestYaml() {
        return """
                subnets:
                  - cidr: 10.10.0.0/24
                    vlan: 110
                    routingZone: zone-a
                    nodes:
                      - hostname: app01
                        ipAddress: 10.10.0.10
                        type: VIRTUAL_MACHINE
                        roles: [kubernetes]
                environments:
                  - name: prod
                    type: PRODUCTION
                systems:
                  - name: Billing
                    components:
                      - name: billing-api
                        version: 1.0.0
                        deployments:
                          - environment: prod
                            hostname: app01
                links: []
                """;
    }
}
