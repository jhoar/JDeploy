package com.jdeploy.service;

import com.jdeploy.domain.DeploymentInstance;
import com.jdeploy.domain.ExecutionEnvironment;
import com.jdeploy.domain.HardwareNode;
import com.jdeploy.domain.Subnet;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphInvariantValidatorTest {

    private final GraphInvariantValidator validator = new GraphInvariantValidator();

    @Test
    void rejectsNodeWithoutClusterRole() {
        HardwareNode node = new HardwareNode(HardwareNode.NodeType.VIRTUAL_MACHINE, "node-1", "10.0.0.2", Set.of("app"));

        assertThrows(InvariantViolationException.class, () -> validator.requireClusterNodeRole(node));
    }

    @Test
    void acceptsGridRoleCaseInsensitively() {
        HardwareNode node = new HardwareNode(HardwareNode.NodeType.VIRTUAL_MACHINE, "node-1", "10.0.0.2", Set.of("GrId"));

        assertDoesNotThrow(() -> validator.requireClusterNodeRole(node));
    }

    @Test
    void validatesSubnetMembership() {
        HardwareNode node = new HardwareNode(HardwareNode.NodeType.VIRTUAL_MACHINE, "node-1", "10.0.0.2", Set.of("grid"));
        Subnet subnet = new Subnet("10.0.0.0/24", "100", "A");

        assertThrows(InvariantViolationException.class, () -> validator.validateSubnetMembership(subnet, node));

        subnet.addNode(node);
        assertDoesNotThrow(() -> validator.validateSubnetMembership(subnet, node));
    }

    @Test
    void validatesDeploymentTarget() {
        ExecutionEnvironment env = new ExecutionEnvironment("prod", ExecutionEnvironment.EnvironmentType.PRODUCTION);
        HardwareNode node = new HardwareNode(HardwareNode.NodeType.VIRTUAL_MACHINE, "node-1", "10.0.0.2", Set.of("grid"));
        DeploymentInstance deployment = new DeploymentInstance(env, node, null);

        assertDoesNotThrow(() -> validator.validateDeploymentTarget(deployment));
        assertThrows(PreconditionViolationException.class, () -> validator.validateDeploymentTarget(null));
    }
}
