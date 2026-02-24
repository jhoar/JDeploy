package com.jdeploy.service;

import com.jdeploy.artifact.ArtifactMetadata;
import com.jdeploy.artifact.ArtifactStorage;
import com.jdeploy.artifact.LocalFilesystemArtifactStorage;
import com.jdeploy.service.dto.DeploymentManifestDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagramGenerationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void buildPlantUmlContainsSubnetsArtifactsAndLinks() {
        DeploymentManifestDto manifest = manifest();

        DiagramGenerationService service = new DiagramGenerationService(
                new NoopStorage(),
                new SimpleMeterRegistry(),
                ObservationRegistry.NOOP);

        String uml = service.buildPlantUml(manifest);

        assertTrue(uml.contains("frame \"10.0.0.0/24 [VLAN app]\""));
        assertTrue(uml.contains("<<physical>>"));
        assertTrue(uml.contains("<<vm>>"));
        assertTrue(uml.contains("<<grid>>"));
        assertTrue(uml.contains("<<k8s>>"));
        assertTrue(uml.contains("<<storage>>"));
        assertTrue(uml.contains("<<switch>>"));
        assertTrue(uml.contains("component \"payments-api\""));
        assertTrue(uml.contains("artifact \"payments-api:1.2.3\""));
        assertTrue(uml.contains("node_app01 --> node_sw01 : 1000Mbps/1ms"));
    }

    @Test
    void generateDeploymentDiagramWritesPlantUmlAndReturnsMetadata() throws Exception {
        DeploymentManifestDto manifest = manifest();
        ArtifactStorage artifactStorage = new LocalFilesystemArtifactStorage(tempDir.toString());

        DiagramGenerationService service = new DiagramGenerationService(
                artifactStorage,
                new SimpleMeterRegistry(),
                ObservationRegistry.NOOP);

        ArtifactMetadata metadata = service.generateDeploymentDiagram(manifest);

        assertTrue(metadata.artifactId().startsWith("deployment-topology-"));
        assertTrue(metadata.artifactId().endsWith(".puml"));
        assertTrue(Files.exists(metadata.path()));
        String fileContent = Files.readString(metadata.path());
        assertTrue(fileContent.contains("@startuml"));
        assertTrue(fileContent.contains("@enduml"));
    }

    private DeploymentManifestDto manifest() {
        return new DeploymentManifestDto(
                List.of(new DeploymentManifestDto.SubnetDto(
                                "10.0.0.0/24", "app", "internal",
                                List.of(
                                        new DeploymentManifestDto.HardwareNodeDto("phy01", "10.0.0.2", "PHYSICAL", List.of("hypervisor")),
                                        new DeploymentManifestDto.HardwareNodeDto("app01", "10.0.0.10", "VIRTUAL_MACHINE", List.of("web")),
                                        new DeploymentManifestDto.HardwareNodeDto("grid01", "10.0.0.20", "GRID", List.of("worker")),
                                        new DeploymentManifestDto.HardwareNodeDto("k8s01", "10.0.0.30", "K8S", List.of("control-plane")),
                                        new DeploymentManifestDto.HardwareNodeDto("nas01", "10.0.0.40", "STORAGE", List.of("nfs")),
                                        new DeploymentManifestDto.HardwareNodeDto("sw01", "10.0.0.254", "SWITCH", List.of("tor"))
                                )
                        )
                ),
                List.of(new DeploymentManifestDto.ExecutionEnvironmentDto("prod", "PRODUCTION")),
                List.of(new DeploymentManifestDto.SoftwareSystemDto(
                        "Payments",
                        List.of(new DeploymentManifestDto.SoftwareComponentDto(
                                "payments-api", "1.2.3",
                                List.of(new DeploymentManifestDto.DeploymentTargetDto("prod", "app01"))
                        ))
                )),
                List.of(new DeploymentManifestDto.NetworkLinkDto("app01", "sw01", 1000, 1))
        );
    }

    private static final class NoopStorage implements ArtifactStorage {
        @Override
        public ArtifactMetadata create(String artifactName, String content) {
            return new ArtifactMetadata(artifactName, Path.of(artifactName), content.length(), Instant.now(), Instant.now());
        }

        @Override
        public ArtifactMetadata readMetadata(String artifactId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean delete(String artifactId) {
            return false;
        }

        @Override
        public List<String> expireOlderThan(Duration maxAge) {
            return List.of();
        }
    }
}
