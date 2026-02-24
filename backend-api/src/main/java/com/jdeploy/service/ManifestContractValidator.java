package com.jdeploy.service;

import com.jdeploy.service.dto.DeploymentManifestDto;
import org.springframework.stereotype.Service;

import java.util.HashSet;
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
            environments.add(env.name());
        }

        Set<String> hostnames = new HashSet<>();
        Set<String> ips = new HashSet<>();
        for (DeploymentManifestDto.SubnetDto subnet : manifest.subnets()) {
            for (DeploymentManifestDto.HardwareNodeDto node : subnet.nodes()) {
                if (!hostnames.add(node.hostname())) {
                    throw new PreconditionViolationException("Duplicate hostname detected: " + node.hostname());
                }
                if (!ips.add(node.ipAddress())) {
                    throw new PreconditionViolationException("Duplicate IP address detected: " + node.ipAddress());
                }
            }
        }

        for (DeploymentManifestDto.SoftwareSystemDto system : manifest.systems()) {
            for (DeploymentManifestDto.SoftwareComponentDto component : system.components()) {
                for (DeploymentManifestDto.DeploymentTargetDto deployment : component.deployments()) {
                    if (!environments.contains(deployment.environment())) {
                        throw new PreconditionViolationException("Missing target environment: " + deployment.environment());
                    }
                }
            }
        }
    }
}
