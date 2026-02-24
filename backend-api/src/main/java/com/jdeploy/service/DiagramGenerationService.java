package com.jdeploy.service;

import com.jdeploy.artifact.ArtifactMetadata;
import com.jdeploy.artifact.ArtifactStorage;
import com.jdeploy.service.dto.DeploymentManifestDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Service
public class DiagramGenerationService {

    private final ArtifactStorage artifactStorage;
    private final Counter artifactGenerationCounter;
    private final ObservationRegistry observationRegistry;

    public DiagramGenerationService(ArtifactStorage artifactStorage,
                                    MeterRegistry meterRegistry,
                                    ObservationRegistry observationRegistry) {
        this.artifactStorage = Objects.requireNonNull(artifactStorage, "artifactStorage must not be null");
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "observationRegistry must not be null");
        if (meterRegistry == null) {
            throw new PreconditionViolationException("meterRegistry is required");
        }
        this.artifactGenerationCounter = Counter.builder("jdeploy.artifacts.generated")
                .description("Number of generated deployment diagram artifacts")
                .register(meterRegistry);
    }

    public ArtifactMetadata generateDeploymentDiagram(DeploymentManifestDto manifest) {
        if (manifest == null) {
            throw new PreconditionViolationException("manifest is required");
        }
        return Observation.createNotStarted("jdeploy.artifact.generate", observationRegistry)
                .observe(() -> {
                    String plantUml = buildPlantUml(manifest);
                    String artifactId = "deployment-topology-" + Instant.now().toEpochMilli() + ".puml";
                    ArtifactMetadata metadata = artifactStorage.create(artifactId, plantUml, Duration.ofDays(7));
                    if (metadata == null) {
                        throw new PostconditionViolationException("Artifact storage returned null metadata for generated deployment diagram");
                    }
                    artifactGenerationCounter.increment();
                    return metadata;
                });
    }

    public String buildPlantUml(DeploymentManifestDto manifest) {
        if (manifest == null) {
            throw new PreconditionViolationException("manifest is required");
        }

        StringBuilder builder = new StringBuilder();
        builder.append("@startuml\n");
        builder.append("title JDeploy Deployment Topology\n\n");
        builder.append("skinparam shadowing false\n");
        builder.append("left to right direction\n\n");

        for (DeploymentManifestDto.SubnetDto subnet : manifest.subnets()) {
            builder.append("frame \"").append(subnet.cidr()).append(" [VLAN ").append(subnet.vlan())
                    .append("]\" as subnet_").append(alias(subnet.cidr())).append(" {\n");
            for (DeploymentManifestDto.HardwareNodeDto node : subnet.nodes()) {
                String nodeAlias = nodeAlias(node.hostname());
                builder.append("  ").append(nodeKeyword(node.type())).append(" \"")
                        .append(node.hostname()).append("\\n")
                        .append(node.ipAddress()).append("\" as ")
                        .append(nodeAlias)
                        .append(" <<").append(nodeStereotype(node.type())).append(">>\n");
            }
            builder.append("}\n\n");
        }

        for (DeploymentManifestDto.SoftwareSystemDto system : manifest.systems()) {
            builder.append("package \"").append(system.name()).append("\" as system_")
                    .append(alias(system.name())).append(" {\n");
            for (DeploymentManifestDto.SoftwareComponentDto component : system.components()) {
                String componentAlias = "component_" + alias(system.name() + "_" + component.name());
                String artifactAlias = "artifact_" + alias(system.name() + "_" + component.name() + "_" + component.version());

                builder.append("  component \"").append(component.name()).append("\" as ")
                        .append(componentAlias).append("\n");
                builder.append("  artifact \"").append(component.name()).append(":")
                        .append(component.version()).append("\" as ")
                        .append(artifactAlias).append("\n");
                builder.append("  ").append(componentAlias).append(" --> ")
                        .append(artifactAlias).append(" : packaged as\n");

                for (DeploymentManifestDto.DeploymentTargetDto deployment : component.deployments()) {
                    builder.append("  ").append(artifactAlias).append(" --> ")
                            .append(nodeAlias(deployment.hostname()))
                            .append(" : deploy@").append(deployment.environment()).append("\n");
                }
            }
            builder.append("}\n\n");
        }

        for (DeploymentManifestDto.NetworkLinkDto link : manifest.links()) {
            builder.append(nodeAlias(link.fromHostname()))
                    .append(" --> ")
                    .append(nodeAlias(link.toHostname()))
                    .append(" : ")
                    .append(link.bandwidthMbps()).append("Mbps/")
                    .append(link.latencyMs()).append("ms\n");
        }

        builder.append("\nlegend left\n");
        builder.append("  <<physical>> Physical node\n");
        builder.append("  <<vm>> Virtual machine\n");
        builder.append("  <<grid>> Grid/cluster node\n");
        builder.append("  <<k8s>> Kubernetes worker/control-plane\n");
        builder.append("  <<storage>> Storage appliance\n");
        builder.append("  <<switch>> Network switch\n");
        builder.append("endlegend\n");
        builder.append("@enduml\n");
        return builder.toString();
    }

    private String nodeAlias(String hostname) {
        return "node_" + alias(hostname);
    }

    private String nodeKeyword(String nodeType) {
        return switch (normalizedType(nodeType)) {
            case "SWITCH" -> "queue";
            case "STORAGE" -> "database";
            default -> "node";
        };
    }

    private String nodeStereotype(String nodeType) {
        return switch (normalizedType(nodeType)) {
            case "PHYSICAL" -> "physical";
            case "VIRTUAL_MACHINE", "VM" -> "vm";
            case "GRID" -> "grid";
            case "KUBERNETES", "K8S" -> "k8s";
            case "STORAGE" -> "storage";
            case "SWITCH" -> "switch";
            default -> "node";
        };
    }

    private String normalizedType(String nodeType) {
        return nodeType == null ? "" : nodeType.trim().toUpperCase(Locale.ROOT);
    }

    private String alias(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
