package com.jdeploy.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainInvariantsTest {

    @Test
    void hardwareNodeRequiresRoles() {
        assertThrows(IllegalArgumentException.class,
                () -> new HardwareNode(HardwareNode.NodeType.VIRTUAL_MACHINE, "node-1", "10.0.0.10", Set.of()));
    }

    @Test
    void executionEnvironmentRequiresNonBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionEnvironment("  ", ExecutionEnvironment.EnvironmentType.PRODUCTION));
    }

    @Test
    void deploymentInstanceBuildsDeterministicKey() {
        ExecutionEnvironment env = new ExecutionEnvironment("prod", ExecutionEnvironment.EnvironmentType.PRODUCTION);
        HardwareNode node = new HardwareNode(HardwareNode.NodeType.VIRTUAL_MACHINE, "node-1", "10.0.0.10", Set.of("app"));

        DeploymentInstance deployment = new DeploymentInstance(env, node);

        assertEquals("prod@node-1", deployment.getDeploymentKey());
    }
}
