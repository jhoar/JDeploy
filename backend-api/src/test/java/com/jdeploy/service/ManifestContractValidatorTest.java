package com.jdeploy.service;

import com.jdeploy.service.dto.DeploymentManifestDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManifestContractValidatorTest {

    private final ManifestContractValidator validator = new ManifestContractValidator();

    @Test
    void shouldRejectDuplicateHostname() {
        DeploymentManifestDto manifest = validManifest(List.of(
                new DeploymentManifestDto.SubnetDto("10.0.0.0/24", "100", "A", List.of(
                        new DeploymentManifestDto.HardwareNodeDto("node-1", "10.0.0.2", "vm", List.of("app")),
                        new DeploymentManifestDto.HardwareNodeDto("node-1", "10.0.0.3", "vm", List.of("app"))
                ))
        ));

        assertThrows(PreconditionViolationException.class, () -> validator.validateForIngestion(manifest));
    }

    @Test
    void shouldRejectMissingTargetEnvironment() {
        DeploymentManifestDto manifest = new DeploymentManifestDto(
                List.of(new DeploymentManifestDto.SubnetDto("10.0.0.0/24", "100", "A", List.of(
                        new DeploymentManifestDto.HardwareNodeDto("node-1", "10.0.0.2", "vm", List.of("app"))
                ))),
                List.of(new DeploymentManifestDto.ExecutionEnvironmentDto("prod", "k8s")),
                List.of(new DeploymentManifestDto.SoftwareSystemDto("billing", List.of(
                        new DeploymentManifestDto.SoftwareComponentDto("api", "1.0.0", List.of(
                                new DeploymentManifestDto.DeploymentTargetDto("qa", "node-1")
                        ))
                ))),
                List.of()
        );

        assertThrows(PreconditionViolationException.class, () -> validator.validateForIngestion(manifest));
    }

    @Test
    void shouldRejectMissingTargetHost() {
        DeploymentManifestDto manifest = new DeploymentManifestDto(
                List.of(new DeploymentManifestDto.SubnetDto("10.0.0.0/24", "100", "A", List.of(
                        new DeploymentManifestDto.HardwareNodeDto("node-1", "10.0.0.2", "vm", List.of("app"))
                ))),
                List.of(new DeploymentManifestDto.ExecutionEnvironmentDto("prod", "k8s")),
                List.of(new DeploymentManifestDto.SoftwareSystemDto("billing", List.of(
                        new DeploymentManifestDto.SoftwareComponentDto("api", "1.0.0", List.of(
                                new DeploymentManifestDto.DeploymentTargetDto("prod", "node-missing")
                        ))
                ))),
                List.of()
        );

        assertThrows(PreconditionViolationException.class, () -> validator.validateForIngestion(manifest));
    }

    @Test
    void shouldRejectConflictingComponentIdentifiersAcrossSystems() {
        DeploymentManifestDto manifest = new DeploymentManifestDto(
                List.of(new DeploymentManifestDto.SubnetDto("10.0.0.0/24", "100", "A", List.of(
                        new DeploymentManifestDto.HardwareNodeDto("node-1", "10.0.0.2", "vm", List.of("app"))
                ))),
                List.of(new DeploymentManifestDto.ExecutionEnvironmentDto("prod", "k8s")),
                List.of(
                        new DeploymentManifestDto.SoftwareSystemDto("billing", List.of(
                                new DeploymentManifestDto.SoftwareComponentDto("api", "1.0.0", List.of(
                                        new DeploymentManifestDto.DeploymentTargetDto("prod", "node-1")
                                ))
                        )),
                        new DeploymentManifestDto.SoftwareSystemDto("orders", List.of(
                                new DeploymentManifestDto.SoftwareComponentDto("api", "1.0.0", List.of(
                                        new DeploymentManifestDto.DeploymentTargetDto("prod", "node-1")
                                ))
                        ))
                ),
                List.of()
        );

        assertThrows(PreconditionViolationException.class, () -> validator.validateForIngestion(manifest));
    }

    @Test
    void shouldRejectNetworkLinksWithMissingReferences() {
        DeploymentManifestDto manifest = new DeploymentManifestDto(
                List.of(new DeploymentManifestDto.SubnetDto("10.0.0.0/24", "100", "A", List.of(
                        new DeploymentManifestDto.HardwareNodeDto("node-1", "10.0.0.2", "vm", List.of("app"))
                ))),
                List.of(new DeploymentManifestDto.ExecutionEnvironmentDto("prod", "k8s")),
                List.of(new DeploymentManifestDto.SoftwareSystemDto("billing", List.of(
                        new DeploymentManifestDto.SoftwareComponentDto("api", "1.0.0", List.of(
                                new DeploymentManifestDto.DeploymentTargetDto("prod", "node-1")
                        ))
                ))),
                List.of(new DeploymentManifestDto.NetworkLinkDto("node-missing", "node-1", 10, 2))
        );

        assertThrows(PreconditionViolationException.class, () -> validator.validateForIngestion(manifest));
    }


    @Test
    void shouldRejectDuplicateEnvironmentNames() {
        DeploymentManifestDto manifest = new DeploymentManifestDto(
                List.of(new DeploymentManifestDto.SubnetDto("10.0.0.0/24", "100", "A", List.of(
                        new DeploymentManifestDto.HardwareNodeDto("node-1", "10.0.0.2", "vm", List.of("app"))
                ))),
                List.of(
                        new DeploymentManifestDto.ExecutionEnvironmentDto("prod", "k8s"),
                        new DeploymentManifestDto.ExecutionEnvironmentDto("prod", "k8s")
                ),
                List.of(new DeploymentManifestDto.SoftwareSystemDto("billing", List.of(
                        new DeploymentManifestDto.SoftwareComponentDto("api", "1.0.0", List.of(
                                new DeploymentManifestDto.DeploymentTargetDto("prod", "node-1")
                        ))
                ))),
                List.of()
        );

        assertThrows(PreconditionViolationException.class, () -> validator.validateForIngestion(manifest));
    }

    @Test
    void shouldRejectDuplicateSystemNames() {
        DeploymentManifestDto manifest = new DeploymentManifestDto(
                List.of(new DeploymentManifestDto.SubnetDto("10.0.0.0/24", "100", "A", List.of(
                        new DeploymentManifestDto.HardwareNodeDto("node-1", "10.0.0.2", "vm", List.of("app"))
                ))),
                List.of(new DeploymentManifestDto.ExecutionEnvironmentDto("prod", "k8s")),
                List.of(
                        new DeploymentManifestDto.SoftwareSystemDto("billing", List.of()),
                        new DeploymentManifestDto.SoftwareSystemDto("billing", List.of())
                ),
                List.of()
        );

        assertThrows(PreconditionViolationException.class, () -> validator.validateForIngestion(manifest));
    }

    @Test
    void shouldAcceptValidManifest() {
        assertDoesNotThrow(() -> validator.validateForIngestion(validManifest(List.of(
                new DeploymentManifestDto.SubnetDto("10.0.0.0/24", "100", "A", List.of(
                        new DeploymentManifestDto.HardwareNodeDto("node-1", "10.0.0.2", "vm", List.of("app"))
                ))
        ))));
    }

    private DeploymentManifestDto validManifest(List<DeploymentManifestDto.SubnetDto> subnets) {
        return new DeploymentManifestDto(
                subnets,
                List.of(new DeploymentManifestDto.ExecutionEnvironmentDto("prod", "k8s")),
                List.of(new DeploymentManifestDto.SoftwareSystemDto("billing", List.of(
                        new DeploymentManifestDto.SoftwareComponentDto("api", "1.0.0", List.of(
                                new DeploymentManifestDto.DeploymentTargetDto("prod", "node-1")
                        ))
                ))),
                List.of()
        );
    }
}
