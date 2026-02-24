package com.jdeploy.service;

import com.jdeploy.artifact.ArtifactMetadata;
import com.jdeploy.artifact.ArtifactStorage;
import com.jdeploy.service.dto.DeploymentManifestDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
        this.artifactGenerationCounter = Counter.builder("jdeploy.artifacts.generated")
                .description("Number of generated deployment diagram artifacts")
                .register(meterRegistry);
    }

    public ArtifactMetadata generateDeploymentDiagram(DeploymentManifestDto manifest) {
        Objects.requireNonNull(manifest, "manifest must not be null");
        return Observation.createNotStarted("jdeploy.artifact.generate", observationRegistry)
                .observe(() -> {
                    String plantUml = buildPlantUml(manifest);
                    String artifactId = "deployment-" + Instant.now().toEpochMilli() + ".puml";
                    ArtifactMetadata metadata = artifactStorage.create(artifactId, plantUml);
                    artifactGenerationCounter.increment();
                    return metadata;
                });
    }

    public String buildPlantUml(DeploymentManifestDto manifest) {
        Objects.requireNonNull(manifest, "manifest must not be null");

        StringBuilder builder = new StringBuilder();
        builder.append("@startuml\n");
        builder.append("title JDeploy Deployment Diagram\n\n");

        for (DeploymentManifestDto.SubnetDto subnet : manifest.subnets()) {
            builder.append("frame \"").append(subnet.cidr()).append(" [VLAN ").append(subnet.vlan())
                    .append("]\" as subnet_").append(alias(subnet.cidr())).append(" {\n");
            for (DeploymentManifestDto.HardwareNodeDto node : subnet.nodes()) {
                builder.append("  node \"").append(node.hostname()).append("\\n")
                        .append(node.ipAddress()).append("\\n")
                        .append(String.join(", ", node.roles()))
                        .append("\" as node_").append(alias(node.hostname())).append("\n");
            }
            builder.append("}\n\n");
        }

        for (DeploymentManifestDto.SoftwareSystemDto system : manifest.systems()) {
            builder.append("package \"").append(system.name()).append("\" {\n");
            for (DeploymentManifestDto.SoftwareComponentDto component : system.components()) {
                String componentAlias = "artifact_" + alias(system.name() + "_" + component.name());
                builder.append("  artifact \"").append(component.name()).append(":")
                        .append(component.version()).append("\" as ").append(componentAlias).append("\n");
                for (DeploymentManifestDto.DeploymentTargetDto deployment : component.deployments()) {
                    builder.append("  ").append(componentAlias).append(" --> node_").append(alias(deployment.hostname()))
                            .append(" : deploy@").append(deployment.environment()).append("\n");
                }
            }
            builder.append("}\n\n");
        }

        for (DeploymentManifestDto.NetworkLinkDto link : manifest.links()) {
            builder.append("node_").append(alias(link.fromHostname()))
                    .append(" --> node_").append(alias(link.toHostname()))
                    .append(" : ").append(link.bandwidthMbps()).append("Mbps/")
                    .append(link.latencyMs()).append("ms\n");
        }

        builder.append("@enduml\n");
        return builder.toString();
    }

    private String alias(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
