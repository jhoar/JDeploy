package com.jdeploy.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Objects;
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
    }

    public void addNode(HardwareNode node) {
        nodes.add(Objects.requireNonNull(node, "node must not be null"));
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
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
