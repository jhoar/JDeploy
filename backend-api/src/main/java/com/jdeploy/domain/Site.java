package com.jdeploy.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Node("Site")
public class Site {

    public enum SiteType {
        SITE,
        DATACENTER
    }

    @Id
    @GeneratedValue
    private Long id;

    private String name;
    private SiteType type;

    @Relationship(type = "HAS_SUBNET")
    private Set<Subnet> subnets = new HashSet<>();

    Site() {
    }

    public Site(String name) {
        this(name, SiteType.SITE);
    }

    public Site(String name, SiteType type) {
        this.name = requireNonBlank(name, "name");
        this.type = Objects.requireNonNull(type, "type must not be null");
    }

    public void addSubnet(Subnet subnet) {
        subnets.add(Objects.requireNonNull(subnet, "subnet must not be null"));
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public SiteType getType() {
        return type;
    }

    public Set<Subnet> getSubnets() {
        return Set.copyOf(subnets);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
