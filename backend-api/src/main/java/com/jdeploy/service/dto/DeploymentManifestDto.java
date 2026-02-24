package com.jdeploy.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "DeploymentManifest")
@JsonIgnoreProperties(ignoreUnknown = false)
public record DeploymentManifestDto(
        @Schema(description = "Network subnets participating in the deployment")
        List<SubnetDto> subnets,
        @Schema(description = "Execution environments where components run")
        List<ExecutionEnvironmentDto> environments,
        @Schema(description = "Business systems and their deployable components")
        List<SoftwareSystemDto> systems,
        @Schema(description = "Connectivity links between hardware nodes")
        List<NetworkLinkDto> links
) {
    public DeploymentManifestDto {
        subnets = subnets == null ? List.of() : List.copyOf(subnets);
        environments = environments == null ? List.of() : List.copyOf(environments);
        systems = systems == null ? List.of() : List.copyOf(systems);
        links = links == null ? List.of() : List.copyOf(links);
    }

    @Schema(name = "Subnet", description = "A subnet boundary and its nodes")
    public record SubnetDto(
            @Schema(example = "10.20.0.0/24") String cidr,
            @Schema(example = "120") String vlan,
            @Schema(example = "internal-dc-a") String routingZone,
            @Schema(description = "Nodes contained in this subnet") List<HardwareNodeDto> nodes) {
        public SubnetDto {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    @Schema(name = "HardwareNode", description = "Deployable infrastructure node")
    public record HardwareNodeDto(
            @Schema(example = "node-a-01") String hostname,
            @Schema(example = "10.20.0.11") String ipAddress,
            @Schema(example = "vm") String type,
            @Schema(description = "Node roles used for placement and constraints") List<String> roles) {
        public HardwareNodeDto {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    @Schema(name = "ExecutionEnvironment", description = "Runtime environment for components")
    public record ExecutionEnvironmentDto(@Schema(example = "prod") String name,
                                          @Schema(example = "kubernetes") String type) {
    }

    @Schema(name = "SoftwareSystem", description = "Logical software system")
    public record SoftwareSystemDto(
            @Schema(example = "billing") String name,
            @Schema(description = "Deployable components of this system") List<SoftwareComponentDto> components) {
        public SoftwareSystemDto {
            components = components == null ? List.of() : List.copyOf(components);
        }
    }

    @Schema(name = "SoftwareComponent", description = "Deployable unit of software")
    public record SoftwareComponentDto(
            @Schema(example = "billing-api") String name,
            @Schema(example = "1.4.2") String version,
            @Schema(description = "Where the component is deployed") List<DeploymentTargetDto> deployments) {
        public SoftwareComponentDto {
            deployments = deployments == null ? List.of() : List.copyOf(deployments);
        }
    }

    @Schema(name = "DeploymentTarget", description = "Concrete environment-node placement")
    public record DeploymentTargetDto(
            @Schema(example = "prod") String environment,
            @Schema(example = "node-a-01") String hostname) {
    }

    @Schema(name = "NetworkLink", description = "Directional connectivity relationship between nodes")
    public record NetworkLinkDto(
            @Schema(example = "node-a-01") String fromHostname,
            @Schema(example = "node-b-07") String toHostname,
            @Schema(example = "1000") int bandwidthMbps,
            @Schema(example = "3") int latencyMs) {
    }
}
