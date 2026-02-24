package com.jdeploy.service;

import com.jdeploy.artifact.ArtifactMetadata;
import com.jdeploy.artifact.ArtifactStorage;
import com.jdeploy.service.dto.DeploymentManifestDto;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagramGenerationServiceTest {

    @Test
    void buildPlantUmlContainsSubnetsArtifactsAndLinks() {
        DeploymentManifestDto manifest = new DeploymentManifestDto(
                List.of(new DeploymentManifestDto.SubnetDto(
                        "10.0.0.0/24", "app", "internal",
                        List.of(new DeploymentManifestDto.HardwareNodeDto("app01", "10.0.0.10", "VIRTUAL_MACHINE", List.of("kubernetes")))
                )),
                List.of(new DeploymentManifestDto.ExecutionEnvironmentDto("prod", "PRODUCTION")),
                List.of(new DeploymentManifestDto.SoftwareSystemDto(
                        "Payments",
                        List.of(new DeploymentManifestDto.SoftwareComponentDto(
                                "payments-api", "1.2.3",
                                List.of(new DeploymentManifestDto.DeploymentTargetDto("prod", "app01"))
                        ))
                )),
                List.of(new DeploymentManifestDto.NetworkLinkDto("app01", "app01", 1000, 1))
        );

        DiagramGenerationService service = new DiagramGenerationService(new NoopStorage());
        String uml = service.buildPlantUml(manifest);

        assertTrue(uml.contains("frame \"10.0.0.0/24 [VLAN app]\""));
        assertTrue(uml.contains("artifact \"payments-api:1.2.3\""));
        assertTrue(uml.contains("node_app01 --> node_app01 : 1000Mbps/1ms"));
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
