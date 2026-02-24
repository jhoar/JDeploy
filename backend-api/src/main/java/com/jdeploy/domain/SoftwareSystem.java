package com.jdeploy.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Objects;
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
    }

    public void addComponent(SoftwareComponent component) {
        components.add(Objects.requireNonNull(component, "component must not be null"));
    }

    public void addClusterNode(HardwareNode clusterNode) {
        clusterNodes.add(Objects.requireNonNull(clusterNode, "clusterNode must not be null"));
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
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
