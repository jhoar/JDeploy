package com.jdeploy.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.Objects;

@Node("DeploymentInstance")
public class DeploymentInstance {

    @Id
    @GeneratedValue
    private Long id;

    private String deploymentKey;

    @Relationship(type = "TARGET_ENVIRONMENT")
    private ExecutionEnvironment targetEnvironment;

    @Relationship(type = "TARGET_NODE")
    private HardwareNode targetNode;

    DeploymentInstance() {
    }

    public DeploymentInstance(ExecutionEnvironment targetEnvironment, HardwareNode targetNode) {
        this.targetEnvironment = Objects.requireNonNull(targetEnvironment, "targetEnvironment must not be null");
        this.targetNode = Objects.requireNonNull(targetNode, "targetNode must not be null");
        this.deploymentKey = targetEnvironment.getName() + "@" + targetNode.getHostname();
    }

    public Long getId() {
        return id;
    }

    public String getDeploymentKey() {
        return deploymentKey;
    }

    public ExecutionEnvironment getTargetEnvironment() {
        return targetEnvironment;
    }

    public HardwareNode getTargetNode() {
        return targetNode;
    }
}
