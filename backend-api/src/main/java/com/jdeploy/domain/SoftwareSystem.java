package com.jdeploy.domain;

import com.jdeploy.service.InvariantViolationException;
import com.jdeploy.service.PostconditionViolationException;
import com.jdeploy.service.PreconditionViolationException;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("SoftwareSystem")
public class SoftwareSystem {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    @Relationship(type = "HAS_COMPONENT")
    private Set<SoftwareComponent> components = new HashSet<>();

    @Relationship(type = "CLUSTER_MEMBER")
    private Set<HardwareNode> clusterNodes = new HashSet<>();

    SoftwareSystem() {
    }

    public SoftwareSystem(String name) {
        this.name = requireNonBlank(name, "name");
        if (this.name.isBlank()) {
            throw new PostconditionViolationException("SoftwareSystem construction failed to initialize required state");
        }
    }

    public void addComponent(SoftwareComponent component) {
        SoftwareComponent requiredComponent = requireNonNull(component, "component");
        components.add(requiredComponent);
        if (components.stream().noneMatch(existing -> existing.getName().equals(requiredComponent.getName())
                && existing.getVersion().equals(requiredComponent.getVersion()))) {
            throw new InvariantViolationException("SoftwareSystem component set invariant violated after component addition");
        }
    }

    public void addClusterNode(HardwareNode clusterNode) {
        HardwareNode requiredNode = requireNonNull(clusterNode, "clusterNode");
        if (requiredNode.getRoles().stream().noneMatch(role -> role.equalsIgnoreCase("grid") || role.equalsIgnoreCase("kubernetes"))) {
            throw new InvariantViolationException("Cluster node must expose either grid or kubernetes role");
        }
        clusterNodes.add(requiredNode);
        if (clusterNodes.stream().noneMatch(existing -> existing.getHostname().equals(requiredNode.getHostname()))) {
            throw new InvariantViolationException("SoftwareSystem cluster membership invariant violated after node addition");
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<SoftwareComponent> getComponents() {
        return Set.copyOf(components);
    }

    public Set<HardwareNode> getClusterNodes() {
        return Set.copyOf(clusterNodes);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null) {
            throw new PreconditionViolationException(field + " is required");
        }
        if (value.isBlank()) {
            throw new PreconditionViolationException(field + " must not be blank");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new PreconditionViolationException(field + " is required");
        }
        return value;
    }
}
