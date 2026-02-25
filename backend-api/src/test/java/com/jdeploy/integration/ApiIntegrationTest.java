package com.jdeploy.integration;

import com.jdeploy.JDeployApplication;
import com.jdeploy.api.ManifestController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(classes = JDeployApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private Neo4jClient neo4jClient;

    @LocalServerPort
    int port;

    @BeforeEach
    void resetGraph() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    }

    @Test
    void ingestionPersistsHeterogeneousTopologyAndServesReadEndpoints() {
        String yaml = manifest("heterogeneous-topology.yaml");
        RestTemplate ingestClient = authenticatedClient("ingest", "ingest-password");

        ResponseEntity<ManifestController.OperationResult> ingestResponse = ingestClient.postForEntity(
                "http://localhost:" + port + "/api/manifests/ingest",
                yaml,
                ManifestController.OperationResult.class);

        assertEquals(HttpStatus.OK, ingestResponse.getStatusCode());
        assertEquals(3L, countNodes("HardwareNode"));
        assertEquals(2L, countNodes("ExecutionEnvironment"));
        assertEquals(2L, countNodes("NetworkLink"));

        RestTemplate readClient = authenticatedClient("reader", "reader-password");
        URI deploymentsUri = UriComponentsBuilder.fromUriString("http://localhost:" + port + "/api/subnets/deployments")
                .queryParam("subnetId", "10.10.0.0/24")
                .build(true)
                .toUri();
        ResponseEntity<List<Map<String, Object>>> deploymentsResponse = readClient.exchange(
                deploymentsUri,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {
                });

        assertEquals(HttpStatus.OK, deploymentsResponse.getStatusCode());
        assertFalse(deploymentsResponse.getBody().isEmpty());

        ResponseEntity<Map<String, Object>> systemDiagramResponse = readClient.exchange(
                "http://localhost:" + port + "/api/diagrams/system/Billing",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {
                });
        assertEquals(HttpStatus.OK, systemDiagramResponse.getStatusCode());
        assertEquals("Billing", systemDiagramResponse.getBody().get("systemName"));

        ResponseEntity<List<Map<String, Object>>> impactSystemsResponse = readClient.exchange(
                "http://localhost:" + port + "/api/impact/node/k8s-worker-01/systems",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {
                });
        assertEquals(HttpStatus.OK, impactSystemsResponse.getStatusCode());
        assertFalse(impactSystemsResponse.getBody().isEmpty());
    }

    @Test
    void endpointsAndAuthorizationRulesAreEnforced() {
        String yaml = manifest("edge-regional-topology.yaml");
        RestTemplate readClient = authenticatedClient("reader", "reader-password");
        RestTemplate ingestClient = authenticatedClient("ingest", "ingest-password");
        RestTemplate generatorClient = authenticatedClient("generator", "generator-password");

        URI unauthorizedUri = UriComponentsBuilder.fromUriString("http://localhost:" + port + "/api/subnets/deployments")
                .queryParam("subnetId", "172.16.10.0/24")
                .build(true)
                .toUri();
        HttpClientErrorException.Unauthorized unauthorizedRead = assertThrows(HttpClientErrorException.Unauthorized.class,
                () -> restTemplate.getForEntity(unauthorizedUri, String.class));
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorizedRead.getStatusCode());

        HttpClientErrorException.Forbidden forbiddenIngest = assertThrows(HttpClientErrorException.Forbidden.class,
                () -> readClient.postForEntity("http://localhost:" + port + "/api/manifests/ingest", yaml, String.class));
        assertEquals(HttpStatus.FORBIDDEN, forbiddenIngest.getStatusCode());

        ResponseEntity<ManifestController.OperationResult> ingestResponse = ingestClient.postForEntity(
                "http://localhost:" + port + "/api/manifests/ingest",
                yaml,
                ManifestController.OperationResult.class);
        assertEquals(HttpStatus.OK, ingestResponse.getStatusCode());

        HttpClientErrorException.Forbidden forbiddenIngestForGenerator = assertThrows(HttpClientErrorException.Forbidden.class,
                () -> generatorClient.postForEntity("http://localhost:" + port + "/api/manifests/ingest", yaml, String.class));
        assertEquals(HttpStatus.FORBIDDEN, forbiddenIngestForGenerator.getStatusCode());

        ResponseEntity<Map<String, Object>> generated = generatorClient.exchange(
                "http://localhost:" + port + "/api/artifacts/generate",
                HttpMethod.POST,
                new HttpEntity<>(yaml),
                new ParameterizedTypeReference<>() {
                });
        assertEquals(HttpStatus.OK, generated.getStatusCode());

        HttpClientErrorException.Forbidden forbiddenGenerateForReader = assertThrows(HttpClientErrorException.Forbidden.class,
                () -> readClient.postForEntity("http://localhost:" + port + "/api/artifacts/generate", yaml, String.class));
        assertEquals(HttpStatus.FORBIDDEN, forbiddenGenerateForReader.getStatusCode());
    }



    @Test
    void topologyDetailRoutesRequireAuthenticationAndAllowReadAuthorities() {
        String yaml = manifest("edge-regional-topology.yaml");
        RestTemplate ingestClient = authenticatedClient("ingest", "ingest-password");
        RestTemplate readClient = authenticatedClient("reader", "reader-password");

        ResponseEntity<ManifestController.OperationResult> ingestResponse = ingestClient.postForEntity(
                "http://localhost:" + port + "/api/manifests/ingest",
                yaml,
                ManifestController.OperationResult.class);
        assertEquals(HttpStatus.OK, ingestResponse.getStatusCode());

        HttpClientErrorException.Unauthorized unauthorizedSystemDetail = assertThrows(HttpClientErrorException.Unauthorized.class,
                () -> restTemplate.getForEntity("http://localhost:" + port + "/api/topology/systems/Telemetry", String.class));
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorizedSystemDetail.getStatusCode());

        ResponseEntity<String> systemDetail = readClient.getForEntity("http://localhost:" + port + "/api/topology/systems/Telemetry", String.class);
        assertEquals(HttpStatus.OK, systemDetail.getStatusCode());

        HttpClientErrorException.Unauthorized unauthorizedNodeDetail = assertThrows(HttpClientErrorException.Unauthorized.class,
                () -> restTemplate.getForEntity("http://localhost:" + port + "/api/topology/nodes/edge-physical-01", String.class));
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorizedNodeDetail.getStatusCode());

        ResponseEntity<String> nodeDetail = readClient.getForEntity("http://localhost:" + port + "/api/topology/nodes/edge-physical-01", String.class);
        assertEquals(HttpStatus.OK, nodeDetail.getStatusCode());

        String subnetWithoutSlash = "edge-10-test";
        neo4jClient.query("CREATE (:Subnet {cidr: $cidr, vlan: 'edge-test', routingZone: 'edge-test-zone'})")
                .bind(subnetWithoutSlash).to("cidr")
                .run();

        String subnetDetailUrl = "http://localhost:" + port + "/api/topology/subnets/" + subnetWithoutSlash;
        HttpClientErrorException.Unauthorized unauthorizedSubnetDetail = assertThrows(HttpClientErrorException.Unauthorized.class,
                () -> restTemplate.getForEntity(subnetDetailUrl, String.class));
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorizedSubnetDetail.getStatusCode());

        ResponseEntity<String> subnetDetail = readClient.getForEntity(subnetDetailUrl, String.class);
        assertEquals(HttpStatus.OK, subnetDetail.getStatusCode());

        HttpClientErrorException.Unauthorized unauthorizedEnvironmentDetail = assertThrows(HttpClientErrorException.Unauthorized.class,
                () -> restTemplate.getForEntity("http://localhost:" + port + "/api/topology/environments/prod", String.class));
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorizedEnvironmentDetail.getStatusCode());

        ResponseEntity<String> environmentDetail = readClient.getForEntity("http://localhost:" + port + "/api/topology/environments/prod", String.class);
        assertEquals(HttpStatus.OK, environmentDetail.getStatusCode());
    }
    @Test
    void artifactGenerationAndDownloadEnforceAuthorities() {
        String yaml = manifest("heterogeneous-topology.yaml");

        RestTemplate generatorClient = authenticatedClient("generator", "generator-password");
        ResponseEntity<Map<String, Object>> generated = generatorClient.exchange(
                "http://localhost:" + port + "/api/artifacts/generate",
                HttpMethod.POST,
                new HttpEntity<>(yaml),
                new ParameterizedTypeReference<>() {
                });
        assertEquals(HttpStatus.OK, generated.getStatusCode());
        String artifactId = String.valueOf(generated.getBody().get("artifactId"));
        assertNotNull(artifactId);

        RestTemplate readClient = authenticatedClient("reader", "reader-password");
        ResponseEntity<String> downloaded = readClient.getForEntity(
                "http://localhost:" + port + "/api/artifacts/" + artifactId,
                String.class);
        assertEquals(HttpStatus.OK, downloaded.getStatusCode());
        assertTrue(downloaded.getBody().contains("@startuml"));
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

        RestTemplate readClient = authenticatedClient("reader", "reader-password");
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
    void deploymentUpdatePreservesCanonicalKeyAndRewritesTargets() {
        String yaml = """
                subnets:
                  - cidr: 10.0.0.0/24
                    vlan: 100
                    routingZone: core
                    nodes:
                      - hostname: app-1
                        ipAddress: 10.0.0.11
                        type: VIRTUAL_MACHINE
                        roles: [app]
                      - hostname: app-2
                        ipAddress: 10.0.0.12
                        type: VIRTUAL_MACHINE
                        roles: [app]
                environments:
                  - name: prod
                    type: PRODUCTION
                  - name: dr
                    type: STAGING
                systems:
                  - name: Billing
                    components:
                      - name: billing-api
                        version: 1.0.0
                        deployments:
                          - environment: prod
                            hostname: app-1
                      - name: billing-worker
                        version: 1.0.0
                        deployments:
                          - environment: prod
                            hostname: app-1
                links: []
                """;

        RestTemplate ingestClient = authenticatedClient("ingest", "ingest-password");
        ResponseEntity<String> ingestResponse = ingestClient.postForEntity(
                "http://localhost:" + port + "/api/manifests/ingest",
                yaml,
                String.class);
        assertEquals(HttpStatus.OK, ingestResponse.getStatusCode());

        String currentKey = "prod@app-1:billing-api:1.0.0";
        ResponseEntity<String> updateResponse = ingestClient.exchange(
                deploymentUpdateUri(currentKey),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("targetEnvironmentName", "dr", "targetHostname", "app-2")),
                String.class);
        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());

        String updatedKey = "dr@app-2:billing-api:1.0.0";
        assertEquals(1L, countDeploymentsByKey(updatedKey));
        assertEquals(0L, countDeploymentsByKey(currentKey));
        assertEquals(1L, countDeploymentsByKey("prod@app-1:billing-worker:1.0.0"));

        assertEquals(0L, countTargetRelationship(updatedKey, "TARGETS", "ExecutionEnvironment", "prod"));
        assertEquals(0L, countTargetRelationship(updatedKey, "TARGETS", "HardwareNode", "app-1"));
        assertEquals(1L, countTargetRelationship(updatedKey, "TARGETS", "ExecutionEnvironment", "dr"));
        assertEquals(1L, countTargetRelationship(updatedKey, "TARGETS", "HardwareNode", "app-2"));
        assertEquals(1L, countTargetRelationship(updatedKey, "TARGET_ENVIRONMENT", "ExecutionEnvironment", "dr"));
        assertEquals(1L, countTargetRelationship(updatedKey, "TARGET_NODE", "HardwareNode", "app-2"));
    }

    @Test
    void deploymentUpdateAllowsSameEnvironmentAndNodeForDifferentComponents() {
        String yaml = """
                subnets:
                  - cidr: 10.1.0.0/24
                    vlan: 101
                    routingZone: core
                    nodes:
                      - hostname: app-a
                        ipAddress: 10.1.0.11
                        type: VIRTUAL_MACHINE
                        roles: [app]
                      - hostname: app-b
                        ipAddress: 10.1.0.12
                        type: VIRTUAL_MACHINE
                        roles: [app]
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
                            hostname: app-a
                      - name: billing-worker
                        version: 1.0.0
                        deployments:
                          - environment: prod
                            hostname: app-b
                links: []
                """;

        RestTemplate ingestClient = authenticatedClient("ingest", "ingest-password");
        ResponseEntity<String> ingestResponse = ingestClient.postForEntity(
                "http://localhost:" + port + "/api/manifests/ingest",
                yaml,
                String.class);
        assertEquals(HttpStatus.OK, ingestResponse.getStatusCode());

        ResponseEntity<String> updateResponse = ingestClient.exchange(
                deploymentUpdateUri("prod@app-a:billing-api:1.0.0"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("targetEnvironmentName", "prod", "targetHostname", "app-b")),
                String.class);
        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());

        assertEquals(1L, countDeploymentsByKey("prod@app-b:billing-api:1.0.0"));
        assertEquals(1L, countDeploymentsByKey("prod@app-b:billing-worker:1.0.0"));
    }

    @Test
    void actuatorMetricsExposeCustomCounters() {
        RestTemplate ingestClient = authenticatedClient("ingest", "ingest-password");
        ingestClient.postForEntity("http://localhost:" + port + "/api/manifests/ingest", manifest("heterogeneous-topology.yaml"), String.class);
        assertThrows(HttpClientErrorException.BadRequest.class,
                () -> ingestClient.postForEntity("http://localhost:" + port + "/api/quality-gates/manifest", "bad: [", String.class));

        RestTemplate readClient = authenticatedClient("reader", "reader-password");
        ResponseEntity<String> prometheus = readClient.getForEntity("http://localhost:" + port + "/actuator/prometheus", String.class);

        assertEquals(HttpStatus.OK, prometheus.getStatusCode());
        assertTrue(prometheus.getBody().contains("jdeploy_ingestion_errors_total"));
    }

    private RestTemplate authenticatedClient(String username, String password) {
        RestTemplate client = new RestTemplate();
        client.getInterceptors().add(new BasicAuthenticationInterceptor(username, password));
        return client;
    }


    private URI deploymentUpdateUri(String deploymentKey) {
        return UriComponentsBuilder.fromUriString("http://localhost:" + port)
                .pathSegment("api", "topology", "deployments", deploymentKey)
                .build()
                .encode()
                .toUri();
    }

    private long countDeploymentsByKey(String deploymentKey) {
        return neo4jClient.query("MATCH (d:DeploymentInstance {deploymentKey: $deploymentKey}) RETURN count(d) AS count")
                .bind(deploymentKey).to("deploymentKey")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("count").asLong())
                .one()
                .orElse(0L);
    }

    private long countTargetRelationship(String deploymentKey, String relationshipType, String label, String value) {
        String keyField = "HardwareNode".equals(label) ? "hostname" : "name";
        return neo4jClient.query("MATCH (d:DeploymentInstance {deploymentKey: $deploymentKey})-[r]->(t:" + label + ") " +
                        "WHERE type(r) = $relationshipType AND t." + keyField + " = $value RETURN count(r) AS count")
                .bind(deploymentKey).to("deploymentKey")
                .bind(relationshipType).to("relationshipType")
                .bind(value).to("value")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("count").asLong())
                .one()
                .orElse(0L);
    }

    private long countNodes(String label) {
        return neo4jClient.query("MATCH (n:" + label + ") RETURN count(n) AS count")
                .fetchAs(Long.class)
                .one()
                .orElse(0L);
    }

    private String manifest(String resourceName) {
        try {
            return new String(getClass().getClassLoader()
                    .getResourceAsStream("manifests/" + resourceName)
                    .readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException ex) {
            throw new IllegalStateException("Failed to load manifest fixture: " + resourceName, ex);
        }
    }
}
