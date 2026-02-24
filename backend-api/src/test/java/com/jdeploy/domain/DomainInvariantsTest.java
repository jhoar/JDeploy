package com.jdeploy.domain;

import com.jdeploy.service.InvariantViolationException;
import com.jdeploy.service.PreconditionViolationException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainInvariantsTest {

    @Test
    void hardwareNodeRequiresRoles() {
        assertThrows(PreconditionViolationException.class,
                () -> new HardwareNode(HardwareNode.NodeType.VIRTUAL_MACHINE, "node-1", "10.0.0.10", Set.of()));
    }

    @Test
    void hardwareNodeRejectsBlankRoleEntries() {
        assertThrows(InvariantViolationException.class,
                () -> new HardwareNode(HardwareNode.NodeType.VIRTUAL_MACHINE, "node-1", "10.0.0.10", Set.of("grid", " ")));
    }

    @Test
    void executionEnvironmentRequiresNonBlankName() {
        assertThrows(PreconditionViolationException.class,
                () -> new ExecutionEnvironment("  ", ExecutionEnvironment.EnvironmentType.PRODUCTION));
    }

    @Test
    void networkLinkMustConnectDistinctNodes() {
        HardwareNode app01 = new HardwareNode(HardwareNode.NodeType.VIRTUAL_MACHINE, "app01", "10.0.0.10", Set.of("grid"));

        assertThrows(InvariantViolationException.class,
                () -> new NetworkLink(1000, 2, app01, app01));
    }

    @Test
    void deploymentInstanceBuildsDeterministicKey() {
        ExecutionEnvironment env = new ExecutionEnvironment("prod", ExecutionEnvironment.EnvironmentType.PRODUCTION);
        HardwareNode node = new HardwareNode(HardwareNode.NodeType.VIRTUAL_MACHINE, "node-1", "10.0.0.10", Set.of("app"));

        DeploymentInstance deployment = new DeploymentInstance(env, node);

        assertEquals("prod@node-1", deployment.getDeploymentKey());
    }
}
