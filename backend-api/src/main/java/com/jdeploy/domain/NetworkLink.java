package com.jdeploy.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.Objects;

@Node("NetworkLink")
public class NetworkLink {

    @Id
    @GeneratedValue
    private Long id;

    private int bandwidthMbps;
    private int latencyMs;

    @Relationship(type = "CONNECTS_FROM")
    private HardwareNode fromNode;

    @Relationship(type = "CONNECTS_TO")
    private HardwareNode toNode;

    NetworkLink() {
    }

    public NetworkLink(int bandwidthMbps, int latencyMs, HardwareNode fromNode, HardwareNode toNode) {
        if (bandwidthMbps <= 0) {
            throw new IllegalArgumentException("bandwidthMbps must be positive");
        }
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must not be negative");
        }
        this.bandwidthMbps = bandwidthMbps;
        this.latencyMs = latencyMs;
        this.fromNode = Objects.requireNonNull(fromNode, "fromNode must not be null");
        this.toNode = Objects.requireNonNull(toNode, "toNode must not be null");
    }

    public Long getId() {
        return id;
    }

    public int getBandwidthMbps() {
        return bandwidthMbps;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public HardwareNode getFromNode() {
        return fromNode;
    }

    public HardwareNode getToNode() {
        return toNode;
    }
}
