package com.jdeploy.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Node("HardwareNode")
public class HardwareNode {

    public enum NodeType {
        PHYSICAL,
        VIRTUAL_MACHINE,
        CONTAINER_HOST,
        GRID_MANAGER,
        KUBERNETES_CONTROL_PLANE,
        KUBERNETES_WORKER
    }

    @Id
    @GeneratedValue
    private Long id;

    private NodeType type;
    private String hostname;
    private String ipAddress;
    private Set<String> roles = new HashSet<>();

    HardwareNode() {
    }

    public HardwareNode(NodeType type, String hostname, String ipAddress, Set<String> roles) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.hostname = requireNonBlank(hostname, "hostname");
        this.ipAddress = requireNonBlank(ipAddress, "ipAddress");
        Objects.requireNonNull(roles, "roles must not be null");
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("roles must not be empty");
        }
        this.roles = new HashSet<>(roles);
    }

    public Long getId() {
        return id;
    }

    public NodeType getType() {
        return type;
    }

    public String getHostname() {
        return hostname;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Set<String> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
