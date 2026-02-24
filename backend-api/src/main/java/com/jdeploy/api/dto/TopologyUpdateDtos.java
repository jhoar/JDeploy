package com.jdeploy.api.dto;

import com.jdeploy.domain.ExecutionEnvironment;
import com.jdeploy.domain.HardwareNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public final class TopologyUpdateDtos {

    private TopologyUpdateDtos() {
    }

    @Schema(name = "SoftwareSystemUpdateRequest", description = "Payload for updating software systems")
    public record SoftwareSystemUpdateRequest(@NotBlank @Schema(example = "payments-platform") String name) {
    }

    @Schema(name = "SoftwareComponentUpdateRequest", description = "Payload for updating software components")
    public record SoftwareComponentUpdateRequest(
            @NotBlank @Schema(example = "payments-api") String name,
            @NotBlank @Schema(example = "2.4.1") String version) {
    }

    @Schema(name = "HardwareNodeUpdateRequest", description = "Payload for updating hardware nodes")
    public record HardwareNodeUpdateRequest(
            @NotNull HardwareNode.NodeType type,
            @NotBlank String hostname,
            @NotBlank String ipAddress,
            @NotEmpty Set<@NotBlank String> roles) {
    }

    @Schema(name = "SubnetUpdateRequest", description = "Payload for updating subnets")
    public record SubnetUpdateRequest(
            @NotBlank String cidr,
            @NotBlank String vlan,
            @NotBlank String routingZone) {
    }

    @Schema(name = "ExecutionEnvironmentUpdateRequest", description = "Payload for updating execution environments")
    public record ExecutionEnvironmentUpdateRequest(
            @NotBlank String name,
            @NotNull ExecutionEnvironment.EnvironmentType type) {
    }

    @Schema(name = "DeploymentInstanceUpdateRequest", description = "Payload for updating deployment instances")
    public record DeploymentInstanceUpdateRequest(
            @NotBlank String targetEnvironmentName,
            @NotBlank String targetHostname) {
    }
}
