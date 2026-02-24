package com.jdeploy.service;

import com.jdeploy.service.dto.DeploymentManifestDto;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ManifestIngestionService {

    private final ManifestParserService parserService;
    private final Neo4jClient neo4jClient;
    private final OperationMetricsService operationMetricsService;
    private final ObservationRegistry observationRegistry;

    public ManifestIngestionService(ManifestParserService parserService,
                                    Neo4jClient neo4jClient,
                                    ObservationRegistry observationRegistry,
                                    OperationMetricsService operationMetricsService) {
        this.parserService = Objects.requireNonNull(parserService, "parserService must not be null");
        this.neo4jClient = Objects.requireNonNull(neo4jClient, "neo4jClient must not be null");
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "observationRegistry must not be null");
        this.operationMetricsService = Objects.requireNonNull(operationMetricsService, "operationMetricsService must not be null");
    }

    public DeploymentManifestDto parseManifest(String yamlText) {
        DeploymentManifestDto manifest = parserService.parseManifest(yamlText);
        if (manifest == null) {
            throw new PostconditionViolationException("Manifest parser returned null for YAML payload");
        }
        return manifest;
    }

    public DeploymentManifestDto parseManifest(Path manifestPath) {
        DeploymentManifestDto manifest = parserService.parseManifest(manifestPath);
        if (manifest == null) {
            throw new PostconditionViolationException("Manifest parser returned null for manifest path");
        }
        return manifest;
    }

    @Transactional
    public void synchronize(DeploymentManifestDto manifest) {
        if (manifest == null) {
            throw new PreconditionViolationException("manifest is required");
        }

        operationMetricsService.recordIngestionRequest();
        try {
            Observation.createNotStarted("jdeploy.manifest.synchronize", observationRegistry)
                    .observe(() -> synchronizeManifest(manifest));
            operationMetricsService.recordIngestionSuccess();
        } catch (RuntimeException ex) {
            operationMetricsService.recordIngestionError();
            throw ex;
        }
    }

    private void synchronizeManifest(DeploymentManifestDto manifest) {
        backfillImplicitClusterSemantics();
        upsertEnvironments(manifest);
        upsertSubnetsAndNodes(manifest);
        upsertClusters(manifest);
        upsertSystemsComponentsAndDeployments(manifest);
        upsertNetworkLinks(manifest);
        pruneObsoleteArtifacts(manifest);
    }

    private void backfillImplicitClusterSemantics() {
        neo4jClient.query("""
                MATCH (d:DeploymentInstance)-[:TARGET_ENVIRONMENT]->(env:ExecutionEnvironment)
                WHERE NOT (d)-[:TARGETS]->(env)
                MERGE (d)-[:TARGETS]->(env)
                """)
                .run();

        neo4jClient.query("""
                MATCH (d:DeploymentInstance)-[:TARGET_NODE]->(n:HardwareNode)
                WHERE NOT (d)-[:TARGETS]->(n)
                MERGE (d)-[:TARGETS]->(n)
                """)
                .run();

        neo4jClient.query("""
                MATCH (n:HardwareNode)
                WHERE n.type IN ['GRID_MANAGER']
                  AND NOT (:GridCluster)-[:HAS_NODE]->(n)
                MERGE (c:GridCluster {name: 'implicit-grid'})
                MERGE (c)-[:HAS_NODE]->(n)
                """)
                .run();

        neo4jClient.query("""
                MATCH (n:HardwareNode)
                WHERE n.type IN ['KUBERNETES_CONTROL_PLANE', 'KUBERNETES_WORKER']
                  AND NOT (:KubernetesCluster)-[:HAS_NODE]->(n)
                MERGE (c:KubernetesCluster {name: 'implicit-kubernetes'})
                MERGE (c)-[:HAS_NODE]->(n)
                """)
                .run();
    }

    private void upsertEnvironments(DeploymentManifestDto manifest) {
        for (DeploymentManifestDto.ExecutionEnvironmentDto environment : manifest.environments()) {
            neo4jClient.query("""
                    MERGE (e:ExecutionEnvironment {name: $name})
                    SET e.type = $type
                    """)
                    .bindAll(Map.of("name", environment.name(), "type", environment.type()))
                    .run();
        }
    }

    private void upsertSubnetsAndNodes(DeploymentManifestDto manifest) {
        for (DeploymentManifestDto.SubnetDto subnet : manifest.subnets()) {
            neo4jClient.query("""
                    MERGE (s:Subnet {cidr: $cidr})
                    SET s.vlan = $vlan,
                        s.routingZone = $routingZone
                    """)
                    .bindAll(Map.of(
                            "cidr", subnet.cidr(),
                            "vlan", subnet.vlan(),
                            "routingZone", subnet.routingZone()
                    ))
                    .run();

            neo4jClient.query("""
                    MATCH (s:Subnet {cidr: $cidr})-[r:CONTAINS_NODE]->(:HardwareNode)
                    DELETE r
                    """)
                    .bind(subnet.cidr()).to("cidr")
                    .run();

            for (DeploymentManifestDto.HardwareNodeDto node : subnet.nodes()) {
                neo4jClient.query("""
                        MERGE (n:HardwareNode {hostname: $hostname})
                        SET n.ipAddress = $ipAddress,
                            n.type = $type,
                            n.roles = $roles
                        """)
                        .bindAll(Map.of(
                                "hostname", node.hostname(),
                                "ipAddress", node.ipAddress(),
                                "type", node.type(),
                                "roles", node.roles()
                        ))
                        .run();

                neo4jClient.query("""
                        MATCH (s:Subnet {cidr: $cidr}), (n:HardwareNode {hostname: $hostname})
                        MERGE (s)-[:CONTAINS_NODE]->(n)
                        """)
                        .bindAll(Map.of("cidr", subnet.cidr(), "hostname", node.hostname()))
                        .run();
            }
        }
    }

    private void upsertClusters(DeploymentManifestDto manifest) {
        for (DeploymentManifestDto.ClusterDto cluster : manifest.clusters()) {
            String clusterLabel = "KUBERNETES".equalsIgnoreCase(cluster.type()) ? "KubernetesCluster" : "GridCluster";

            neo4jClient.query("""
                    MERGE (c:%s {name: $clusterName})
                    SET c.type = $clusterType
                    """.formatted(clusterLabel))
                    .bindAll(Map.of("clusterName", cluster.name(), "clusterType", cluster.type()))
                    .run();

            neo4jClient.query("""
                    MATCH (c:%s {name: $clusterName})-[r:HAS_NODE]->(:HardwareNode)
                    DELETE r
                    """.formatted(clusterLabel))
                    .bind(cluster.name()).to("clusterName")
                    .run();

            for (String hostname : cluster.nodes()) {
                neo4jClient.query("""
                        MATCH (c:%s {name: $clusterName}), (n:HardwareNode {hostname: $hostname})
                        MERGE (c)-[:HAS_NODE]->(n)
                        """.formatted(clusterLabel))
                        .bindAll(Map.of("clusterName", cluster.name(), "hostname", hostname))
                        .run();
            }

            if ("KUBERNETES".equalsIgnoreCase(cluster.type())) {
                for (String namespace : cluster.namespaces()) {
                    neo4jClient.query("""
                            MERGE (ns:KubernetesNamespace {name: $namespace})
                            WITH ns
                            MATCH (c:KubernetesCluster {name: $clusterName})
                            MERGE (ns)-[:BELONGS_TO]->(c)
                            """)
                            .bindAll(Map.of("namespace", namespace, "clusterName", cluster.name()))
                            .run();
                }
            }
        }
    }

    private void upsertSystemsComponentsAndDeployments(DeploymentManifestDto manifest) {
        for (DeploymentManifestDto.SoftwareSystemDto system : manifest.systems()) {
            neo4jClient.query("""
                    MERGE (s:SoftwareSystem {name: $name})
                    """)
                    .bind(system.name()).to("name")
                    .run();

            neo4jClient.query("""
                    MATCH (s:SoftwareSystem {name: $name})-[r:HAS_COMPONENT]->(:SoftwareComponent)
                    DELETE r
                    """)
                    .bind(system.name()).to("name")
                    .run();

            for (DeploymentManifestDto.SoftwareComponentDto component : system.components()) {
                neo4jClient.query("""
                        MERGE (c:SoftwareComponent {name: $name, version: $version})
                        """)
                        .bindAll(Map.of("name", component.name(), "version", component.version()))
                        .run();

                neo4jClient.query("""
                        MATCH (s:SoftwareSystem {name: $systemName}), (c:SoftwareComponent {name: $name, version: $version})
                        MERGE (s)-[:HAS_COMPONENT]->(c)
                        """)
                        .bindAll(Map.of(
                                "systemName", system.name(),
                                "name", component.name(),
                                "version", component.version()
                        ))
                        .run();

                neo4jClient.query("""
                        MATCH (c:SoftwareComponent {name: $name, version: $version})-[r:HAS_DEPLOYMENT]->(:DeploymentInstance)
                        DELETE r
                        """)
                        .bindAll(Map.of("name", component.name(), "version", component.version()))
                        .run();

                for (DeploymentManifestDto.DeploymentTargetDto deployment : component.deployments()) {
                    String deploymentKey = deployment.environment() + "@" + deployment.hostname() + ":" + component.name() + ":" + component.version();
                    neo4jClient.query("""
                            MERGE (d:DeploymentInstance {deploymentKey: $deploymentKey})
                            """)
                            .bind(deploymentKey).to("deploymentKey")
                            .run();

                    neo4jClient.query("""
                            MATCH (d:DeploymentInstance {deploymentKey: $deploymentKey})-[r:TARGET_ENVIRONMENT|TARGET_NODE|TARGETS|TARGET_NAMESPACE]->()
                            DELETE r
                            """)
                            .bind(deploymentKey).to("deploymentKey")
                            .run();

                    neo4jClient.query("""
                            MATCH (c:SoftwareComponent {name: $componentName, version: $version}),
                                  (d:DeploymentInstance {deploymentKey: $deploymentKey}),
                                  (e:ExecutionEnvironment {name: $environmentName}),
                                  (n:HardwareNode {hostname: $hostname})
                            MERGE (c)-[:HAS_DEPLOYMENT]->(d)
                            MERGE (d)-[:TARGET_ENVIRONMENT]->(e)
                            MERGE (d)-[:TARGET_NODE]->(n)
                            MERGE (d)-[:TARGETS]->(e)
                            MERGE (d)-[:TARGETS]->(n)
                            """)
                            .bindAll(Map.of(
                                    "componentName", component.name(),
                                    "version", component.version(),
                                    "deploymentKey", deploymentKey,
                                    "environmentName", deployment.environment(),
                                    "hostname", deployment.hostname()
                            ))
                            .run();

                    if (deployment.namespace() != null && !deployment.namespace().isBlank()) {
                        neo4jClient.query("""
                                MERGE (ns:KubernetesNamespace {name: $namespace})
                                """)
                                .bind(deployment.namespace()).to("namespace")
                                .run();

                        neo4jClient.query("""
                                MATCH (d:DeploymentInstance {deploymentKey: $deploymentKey}),
                                      (ns:KubernetesNamespace {name: $namespace})
                                MERGE (d)-[:TARGET_NAMESPACE]->(ns)
                                MERGE (d)-[:TARGETS]->(ns)
                                """)
                                .bindAll(Map.of("deploymentKey", deploymentKey, "namespace", deployment.namespace()))
                                .run();

                        if (deployment.cluster() != null && !deployment.cluster().isBlank()) {
                            neo4jClient.query("""
                                    MATCH (ns:KubernetesNamespace {name: $namespace})
                                    MERGE (kc:KubernetesCluster {name: $cluster})
                                    MERGE (ns)-[:BELONGS_TO]->(kc)
                                    """)
                                    .bindAll(Map.of("namespace", deployment.namespace(), "cluster", deployment.cluster()))
                                    .run();
                        }
                    }
                }
            }
        }
    }

    private void upsertNetworkLinks(DeploymentManifestDto manifest) {
        for (DeploymentManifestDto.NetworkLinkDto link : manifest.links()) {
            String linkKey = link.fromHostname() + "->" + link.toHostname();
            neo4jClient.query("""
                    MATCH (from:HardwareNode {hostname: $fromHostname}),
                          (to:HardwareNode {hostname: $toHostname})
                    MERGE (l:NetworkLink {linkKey: $linkKey})
                    SET l.bandwidthMbps = $bandwidthMbps,
                        l.latencyMs = $latencyMs
                    WITH l, from, to
                    MATCH (l)-[old:CONNECTS_FROM|CONNECTS_TO]->()
                    DELETE old
                    MERGE (l)-[:CONNECTS_FROM]->(from)
                    MERGE (l)-[:CONNECTS_TO]->(to)
                    """)
                    .bindAll(Map.of(
                            "fromHostname", link.fromHostname(),
                            "toHostname", link.toHostname(),
                            "linkKey", linkKey,
                            "bandwidthMbps", link.bandwidthMbps(),
                            "latencyMs", link.latencyMs()
                    ))
                    .run();
        }
    }

    private void pruneObsoleteArtifacts(DeploymentManifestDto manifest) {
        List<String> deploymentKeys = manifest.systems().stream()
                .flatMap(system -> system.components().stream()
                        .flatMap(component -> component.deployments().stream()
                                .map(target -> target.environment() + "@" + target.hostname() + ":" + component.name() + ":" + component.version())))
                .toList();
        neo4jClient.query("""
                MATCH (d:DeploymentInstance)
                WHERE NOT d.deploymentKey IN $deploymentKeys
                DETACH DELETE d
                """)
                .bind(deploymentKeys).to("deploymentKeys")
                .run();

        List<String> linkKeys = manifest.links().stream()
                .map(link -> link.fromHostname() + "->" + link.toHostname())
                .toList();
        neo4jClient.query("""
                MATCH (l:NetworkLink)
                WHERE NOT l.linkKey IN $linkKeys
                DETACH DELETE l
                """)
                .bind(linkKeys).to("linkKeys")
                .run();
    }
}
