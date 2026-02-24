package com.jdeploy.service;

import com.jdeploy.service.dto.DeploymentManifestDto;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class ManifestContractValidator {

    public void validateForIngestion(DeploymentManifestDto manifest) {
        if (manifest == null) {
            throw new PreconditionViolationException("Manifest payload is required");
        }
        if (manifest.systems().isEmpty()) {
            throw new PreconditionViolationException("Manifest must include at least one software system");
        }

        Set<String> environments = new HashSet<>();
        for (DeploymentManifestDto.ExecutionEnvironmentDto env : manifest.environments()) {
            if (!environments.add(env.name())) {
                throw new PreconditionViolationException("Conflicting environment identifier: " + env.name());
            }
        }

        Set<String> hostnames = new HashSet<>();
        Set<String> ips = new HashSet<>();
        for (DeploymentManifestDto.SubnetDto subnet : manifest.subnets()) {
            for (DeploymentManifestDto.HardwareNodeDto node : subnet.nodes()) {
                if (!hostnames.add(node.hostname())) {
                    throw new PreconditionViolationException("Conflicting hardware node identifier: " + node.hostname());
                }
                if (!ips.add(node.ipAddress())) {
                    throw new PreconditionViolationException("Duplicate IP address detected: " + node.ipAddress());
                }
            }
        }

        Set<String> systems = new HashSet<>();
        Map<String, String> componentOwners = new HashMap<>();
        for (DeploymentManifestDto.SoftwareSystemDto system : manifest.systems()) {
            if (!systems.add(system.name())) {
                throw new PreconditionViolationException("Conflicting software system identifier: " + system.name());
            }
            for (DeploymentManifestDto.SoftwareComponentDto component : system.components()) {
                String componentKey = component.name() + ":" + component.version();
                String existingOwner = componentOwners.putIfAbsent(componentKey, system.name());
                if (existingOwner != null && !existingOwner.equals(system.name())) {
                    throw new PreconditionViolationException("Conflicting component identifier across systems: " + componentKey);
                }
                for (DeploymentManifestDto.DeploymentTargetDto deployment : component.deployments()) {
                    if (!environments.contains(deployment.environment())) {
                        throw new PreconditionViolationException("Missing target environment: " + deployment.environment());
                    }
                    if (!hostnames.contains(deployment.hostname())) {
                        throw new PreconditionViolationException("Missing target host: " + deployment.hostname());
                    }
                }
            }
        }

        for (DeploymentManifestDto.NetworkLinkDto link : manifest.links()) {
            if (!hostnames.contains(link.fromHostname())) {
                throw new PreconditionViolationException("Missing network link source host: " + link.fromHostname());
            }
            if (!hostnames.contains(link.toHostname())) {
                throw new PreconditionViolationException("Missing network link destination host: " + link.toHostname());
            }
        }
    }
}
