package com.jdeploy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jdeploy.service.dto.DeploymentManifestDto;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

@Service
public class ManifestIngestionService {

    private final ObjectMapper yamlMapper;
    private final Neo4jClient neo4jClient;

    public ManifestIngestionService(Neo4jClient neo4jClient) {
        this.neo4jClient = Objects.requireNonNull(neo4jClient, "neo4jClient must not be null");
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    public DeploymentManifestDto parseManifest(String yamlText) {
        Objects.requireNonNull(yamlText, "yamlText must not be null");
        try {
            return yamlMapper.readValue(yamlText, DeploymentManifestDto.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to parse deployment manifest yaml", ex);
        }
    }

    public DeploymentManifestDto parseManifest(Path manifestPath) {
        Objects.requireNonNull(manifestPath, "manifestPath must not be null");
        try {
            return parseManifest(Files.readString(manifestPath));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read deployment manifest file", ex);
        }
    }

    @Transactional
    public void synchronize(DeploymentManifestDto manifest) {
        Objects.requireNonNull(manifest, "manifest must not be null");

        for (DeploymentManifestDto.ExecutionEnvironmentDto environment : manifest.environments()) {
            neo4jClient.query("""
                    MERGE (e:ExecutionEnvironment {name: $name})
                    SET e.type = $type
                    """)
                    .bindAll(Map.of("name", environment.name(), "type", environment.type()))
                    .run();
        }

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

        for (DeploymentManifestDto.SoftwareSystemDto system : manifest.systems()) {
            neo4jClient.query("""
                    MERGE (s:SoftwareSystem {name: $name})
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

                for (DeploymentManifestDto.DeploymentTargetDto deployment : component.deployments()) {
                    String deploymentKey = deployment.environment() + "@" + deployment.hostname() + ":" + component.name();
                    neo4jClient.query("""
                            MERGE (d:DeploymentInstance {deploymentKey: $deploymentKey})
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

        for (DeploymentManifestDto.NetworkLinkDto link : manifest.links()) {
            String linkKey = link.fromHostname() + "->" + link.toHostname();
            neo4jClient.query("""
                    MATCH (from:HardwareNode {hostname: $fromHostname}),
                          (to:HardwareNode {hostname: $toHostname})
                    MERGE (l:NetworkLink {linkKey: $linkKey})
                    SET l.bandwidthMbps = $bandwidthMbps,
                        l.latencyMs = $latencyMs
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
}
