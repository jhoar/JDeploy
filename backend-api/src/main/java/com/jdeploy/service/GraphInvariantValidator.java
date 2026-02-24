package com.jdeploy.service;

import com.jdeploy.domain.DeploymentInstance;
import com.jdeploy.domain.HardwareNode;
import com.jdeploy.domain.Subnet;
import org.springframework.stereotype.Service;

@Service
public class GraphInvariantValidator {

    public void validateDeploymentTarget(DeploymentInstance deploymentInstance) {
        if (deploymentInstance == null) {
            throw new PreconditionViolationException("deploymentInstance is required");
        }
        if (deploymentInstance.getTargetEnvironment() == null || deploymentInstance.getTargetNode() == null) {
            throw new InvariantViolationException("DeploymentInstance invariant violated: both target environment and target node are required");
        }
        if (deploymentInstance.getDeploymentKey() == null || deploymentInstance.getDeploymentKey().isBlank()) {
            throw new PostconditionViolationException("Deployment target validation requires a non-blank deployment key");
        }
    }

    public void validateSubnetMembership(Subnet subnet, HardwareNode node) {
        if (subnet == null) {
            throw new PreconditionViolationException("subnet is required");
        }
        if (node == null) {
            throw new PreconditionViolationException("node is required");
        }
        if (subnet.getNodes().stream().noneMatch(existing -> existing.getHostname().equals(node.getHostname()))) {
            throw new InvariantViolationException("Node must belong to subnet before deployment mapping: " + node.getHostname());
        }
    }

    public void requireClusterNodeRole(HardwareNode node) {
        if (node == null) {
            throw new PreconditionViolationException("node is required");
        }
        if (node.getRoles().stream().noneMatch(role -> role.equalsIgnoreCase("grid") || role.equalsIgnoreCase("kubernetes"))) {
            throw new InvariantViolationException("Cluster membership requires grid or kubernetes role for node: " + node.getHostname());
        }
    }
}
