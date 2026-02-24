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

@Node("Subnet")
public class Subnet {

    @Id
    @GeneratedValue
    private Long id;

    private String cidr;
    private String vlan;
    private String routingZone;

    @Relationship(type = "CONTAINS_NODE")
    private Set<HardwareNode> nodes = new HashSet<>();

    Subnet() {
    }

    public Subnet(String cidr, String vlan, String routingZone) {
        this.cidr = requireNonBlank(cidr, "cidr");
        this.vlan = requireNonBlank(vlan, "vlan");
        this.routingZone = requireNonBlank(routingZone, "routingZone");
        if (this.cidr.isBlank() || this.vlan.isBlank() || this.routingZone.isBlank()) {
            throw new PostconditionViolationException("Subnet construction failed to initialize required state");
        }
    }

    public void addNode(HardwareNode node) {
        HardwareNode requiredNode = requireNonNull(node, "node");
        nodes.add(requiredNode);
        if (nodes.stream().noneMatch(existing -> existing.getHostname().equals(requiredNode.getHostname()))) {
            throw new InvariantViolationException("Subnet node membership invariant violated after node addition");
        }
    }

    public Long getId() {
        return id;
    }

    public String getCidr() {
        return cidr;
    }

    public String getVlan() {
        return vlan;
    }

    public String getRoutingZone() {
        return routingZone;
    }

    public Set<HardwareNode> getNodes() {
        return Set.copyOf(nodes);
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
