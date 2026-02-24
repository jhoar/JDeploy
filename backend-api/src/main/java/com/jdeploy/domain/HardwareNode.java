package com.jdeploy.domain;

import com.jdeploy.service.InvariantViolationException;
import com.jdeploy.service.PostconditionViolationException;
import com.jdeploy.service.PreconditionViolationException;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.Collections;
import java.util.HashSet;
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
        this.type = requireNonNull(type, "type");
        this.hostname = requireNonBlank(hostname, "hostname");
        this.ipAddress = requireNonBlank(ipAddress, "ipAddress");
        Set<String> requiredRoles = requireNonNull(roles, "roles");
        if (requiredRoles.isEmpty()) {
            throw new PreconditionViolationException("roles must not be empty");
        }
        this.roles = new HashSet<>(requiredRoles);
        if (this.roles.stream().anyMatch(role -> role == null || role.isBlank())) {
            throw new InvariantViolationException("HardwareNode roles cannot contain null or blank entries");
        }
        if (this.type == null || this.hostname.isBlank() || this.ipAddress.isBlank() || this.roles.isEmpty()) {
            throw new PostconditionViolationException("HardwareNode construction failed to initialize required state");
        }
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
