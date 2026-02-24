package com.jdeploy.service;

import com.jdeploy.domain.DeploymentInstance;
import com.jdeploy.domain.HardwareNode;
import com.jdeploy.domain.Subnet;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class GraphInvariantValidator {

    public void validateDeploymentTarget(DeploymentInstance deploymentInstance) {
        Objects.requireNonNull(deploymentInstance, "deploymentInstance must not be null");
        if (deploymentInstance.getTargetEnvironment() == null || deploymentInstance.getTargetNode() == null) {
            throw new PreconditionViolationException("DeploymentInstance requires both target environment and target node");
        }
    }

    public void validateSubnetMembership(Subnet subnet, HardwareNode node) {
        Objects.requireNonNull(subnet, "subnet must not be null");
        Objects.requireNonNull(node, "node must not be null");
        if (subnet.getNodes().stream().noneMatch(existing -> existing.getHostname().equals(node.getHostname()))) {
            throw new PreconditionViolationException("Node must belong to subnet before deployment mapping");
        }
    }

    public void requireClusterNodeRole(HardwareNode node) {
        Objects.requireNonNull(node, "node must not be null");
        if (node.getRoles().stream().noneMatch(role -> role.equalsIgnoreCase("grid") || role.equalsIgnoreCase("kubernetes"))) {
            throw new PreconditionViolationException("Cluster membership requires grid or kubernetes role");
        }
    }
}
