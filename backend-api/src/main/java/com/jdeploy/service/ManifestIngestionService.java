package com.jdeploy.service;

import com.jdeploy.service.dto.DeploymentManifestDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final Counter ingestionErrorCounter;
    private final ObservationRegistry observationRegistry;

    public ManifestIngestionService(ManifestParserService parserService,
                                    Neo4jClient neo4jClient,
                                    MeterRegistry meterRegistry,
                                    ObservationRegistry observationRegistry) {
        this.parserService = Objects.requireNonNull(parserService, "parserService must not be null");
        this.neo4jClient = Objects.requireNonNull(neo4jClient, "neo4jClient must not be null");
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "observationRegistry must not be null");
        if (meterRegistry == null) {
            throw new PreconditionViolationException("meterRegistry is required");
        }
        this.ingestionErrorCounter = Counter.builder("jdeploy.ingestion.errors")
                .description("Number of manifest ingestion and parsing errors")
                .register(meterRegistry);
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

        try {
            Observation.createNotStarted("jdeploy.manifest.synchronize", observationRegistry)
                    .observe(() -> synchronizeManifest(manifest));
        } catch (RuntimeException ex) {
            ingestionErrorCounter.increment();
            throw ex;
        }
    }

    private void synchronizeManifest(DeploymentManifestDto manifest) {
        upsertEnvironments(manifest);
        upsertSubnetsAndNodes(manifest);
        upsertSystemsComponentsAndDeployments(manifest);
        upsertNetworkLinks(manifest);
        pruneObsoleteArtifacts(manifest);
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
                            MATCH (d:DeploymentInstance {deploymentKey: $deploymentKey})-[r:TARGET_ENVIRONMENT|TARGET_NODE]->()
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
                            """)
                            .bindAll(Map.of(
                                    "componentName", component.name(),
                                    "version", component.version(),
                                    "deploymentKey", deploymentKey,
                                    "environmentName", deployment.environment(),
                                    "hostname", deployment.hostname()
                            ))
                            .run();
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
