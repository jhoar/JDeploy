package com.jdeploy.service;

import com.jdeploy.domain.DeploymentInstance;
import com.jdeploy.domain.ExecutionEnvironment;
import com.jdeploy.domain.HardwareNode;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class DeploymentMappingService {

    private final GraphInvariantValidator graphInvariantValidator;

    public DeploymentMappingService(GraphInvariantValidator graphInvariantValidator) {
        this.graphInvariantValidator = Objects.requireNonNull(graphInvariantValidator, "graphInvariantValidator must not be null");
    }

    public DeploymentInstance mapToTarget(ExecutionEnvironment environment, HardwareNode node) {
        if (environment == null || node == null) {
            throw new PreconditionViolationException("Both environment and node are required for deployment target mapping");
        }
        DeploymentInstance deploymentInstance = new DeploymentInstance(environment, node);
        graphInvariantValidator.validateDeploymentTarget(deploymentInstance);
        return deploymentInstance;
    }
}
