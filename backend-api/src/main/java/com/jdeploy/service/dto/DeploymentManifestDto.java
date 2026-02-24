package com.jdeploy.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "DeploymentManifest")
@JsonIgnoreProperties(ignoreUnknown = false)
public record DeploymentManifestDto(
        List<SubnetDto> subnets,
        List<ExecutionEnvironmentDto> environments,
        List<SoftwareSystemDto> systems,
        List<NetworkLinkDto> links
) {
    public DeploymentManifestDto {
        subnets = subnets == null ? List.of() : List.copyOf(subnets);
        environments = environments == null ? List.of() : List.copyOf(environments);
        systems = systems == null ? List.of() : List.copyOf(systems);
        links = links == null ? List.of() : List.copyOf(links);
    }

    @Schema(name = "Subnet")
    public record SubnetDto(String cidr, String vlan, String routingZone, List<HardwareNodeDto> nodes) {
        public SubnetDto {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    @Schema(name = "HardwareNode")
    public record HardwareNodeDto(String hostname, String ipAddress, String type, List<String> roles) {
        public HardwareNodeDto {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    @Schema(name = "ExecutionEnvironment")
    public record ExecutionEnvironmentDto(String name, String type) {
    }

    @Schema(name = "SoftwareSystem")
    public record SoftwareSystemDto(String name, List<SoftwareComponentDto> components) {
        public SoftwareSystemDto {
            components = components == null ? List.of() : List.copyOf(components);
        }
    }

    @Schema(name = "SoftwareComponent")
    public record SoftwareComponentDto(String name, String version, List<DeploymentTargetDto> deployments) {
        public SoftwareComponentDto {
            deployments = deployments == null ? List.of() : List.copyOf(deployments);
        }
    }

    @Schema(name = "DeploymentTarget")
    public record DeploymentTargetDto(String environment, String hostname) {
    }

    @Schema(name = "NetworkLink")
    public record NetworkLinkDto(String fromHostname, String toHostname, int bandwidthMbps, int latencyMs) {
    }
}
