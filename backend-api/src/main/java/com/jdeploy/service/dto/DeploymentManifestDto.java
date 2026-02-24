package com.jdeploy.service.dto;

import java.util.List;

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

    public record SubnetDto(String cidr, String vlan, String routingZone, List<HardwareNodeDto> nodes) {
        public SubnetDto {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    public record HardwareNodeDto(String hostname, String ipAddress, String type, List<String> roles) {
        public HardwareNodeDto {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    public record ExecutionEnvironmentDto(String name, String type) {
    }

    public record SoftwareSystemDto(String name, List<SoftwareComponentDto> components) {
        public SoftwareSystemDto {
            components = components == null ? List.of() : List.copyOf(components);
        }
    }

    public record SoftwareComponentDto(String name, String version, List<DeploymentTargetDto> deployments) {
        public SoftwareComponentDto {
            deployments = deployments == null ? List.of() : List.copyOf(deployments);
        }
    }

    public record DeploymentTargetDto(String environment, String hostname) {
    }

    public record NetworkLinkDto(String fromHostname, String toHostname, int bandwidthMbps, int latencyMs) {
    }
}
