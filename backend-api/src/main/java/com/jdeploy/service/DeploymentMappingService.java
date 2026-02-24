package com.jdeploy.service;

import com.jdeploy.domain.DeploymentInstance;
import com.jdeploy.domain.ExecutionEnvironment;
import com.jdeploy.domain.HardwareNode;
import org.springframework.stereotype.Service;

@Service
public class DeploymentMappingService {

    private final GraphInvariantValidator graphInvariantValidator;

    public DeploymentMappingService(GraphInvariantValidator graphInvariantValidator) {
        if (graphInvariantValidator == null) {
            throw new PreconditionViolationException("graphInvariantValidator is required");
        }
        this.graphInvariantValidator = graphInvariantValidator;
    }

    public DeploymentInstance mapToTarget(ExecutionEnvironment environment, HardwareNode node) {
        if (environment == null) {
            throw new PreconditionViolationException("environment is required for deployment target mapping");
        }
        if (node == null) {
            throw new PreconditionViolationException("node is required for deployment target mapping");
        }
        DeploymentInstance deploymentInstance = new DeploymentInstance(environment, node);
        graphInvariantValidator.validateDeploymentTarget(deploymentInstance);
        if (deploymentInstance.getTargetEnvironment() != environment || deploymentInstance.getTargetNode() != node) {
            throw new PostconditionViolationException("Deployment mapping postcondition failed: mapped target does not match requested inputs");
        }
        return deploymentInstance;
    }
}
