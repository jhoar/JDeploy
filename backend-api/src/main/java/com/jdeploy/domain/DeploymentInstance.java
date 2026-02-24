package com.jdeploy.domain;

import com.jdeploy.service.PostconditionViolationException;
import com.jdeploy.service.PreconditionViolationException;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

@Node("DeploymentInstance")
public class DeploymentInstance {

    @Id
    @GeneratedValue
    private Long id;

    private String deploymentKey;

    @Relationship(type = "TARGETS")
    private ExecutionEnvironment targetEnvironment;

    @Relationship(type = "TARGETS")
    private HardwareNode targetNode;

    @Relationship(type = "TARGETS")
    private KubernetesNamespace targetNamespace;

    DeploymentInstance() {
    }

    public DeploymentInstance(ExecutionEnvironment targetEnvironment, HardwareNode targetNode, KubernetesNamespace targetNamespace) {
        this.targetEnvironment = requireNonNull(targetEnvironment, "targetEnvironment");
        this.targetNode = requireNonNull(targetNode, "targetNode");
        this.targetNamespace = targetNamespace;
        this.deploymentKey = this.targetEnvironment.getName() + "@" + this.targetNode.getHostname();
        if (this.deploymentKey.isBlank()) {
            throw new PostconditionViolationException("DeploymentInstance must produce a non-blank deployment key");
        }
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

    public KubernetesNamespace getTargetNamespace() {
        return targetNamespace;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new PreconditionViolationException(field + " is required");
        }
        return value;
    }
}
