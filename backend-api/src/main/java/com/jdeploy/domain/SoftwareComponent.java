package com.jdeploy.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Node("SoftwareComponent")
public class SoftwareComponent {

    @Id
    @GeneratedValue
    private Long id;

    private String name;
    private String version;

    @Relationship(type = "HAS_DEPLOYMENT")
    private Set<DeploymentInstance> deployments = new HashSet<>();

    SoftwareComponent() {
    }

    public SoftwareComponent(String name, String version) {
        this.name = requireNonBlank(name, "name");
        this.version = requireNonBlank(version, "version");
    }

    public void addDeployment(DeploymentInstance deployment) {
        deployments.add(Objects.requireNonNull(deployment, "deployment must not be null"));
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public Set<DeploymentInstance> getDeployments() {
        return Set.copyOf(deployments);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
