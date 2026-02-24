package com.jdeploy.domain;

import com.jdeploy.service.InvariantViolationException;
import com.jdeploy.service.PostconditionViolationException;
import com.jdeploy.service.PreconditionViolationException;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

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
            throw new PreconditionViolationException("bandwidthMbps must be positive");
        }
        if (latencyMs < 0) {
            throw new PreconditionViolationException("latencyMs must not be negative");
        }
        this.bandwidthMbps = bandwidthMbps;
        this.latencyMs = latencyMs;
        this.fromNode = requireNonNull(fromNode, "fromNode");
        this.toNode = requireNonNull(toNode, "toNode");
        if (this.fromNode.getHostname().equals(this.toNode.getHostname())) {
            throw new InvariantViolationException("NetworkLink must connect two distinct hardware nodes");
        }
        if (this.bandwidthMbps <= 0 || this.latencyMs < 0) {
            throw new PostconditionViolationException("NetworkLink construction failed to preserve link constraints");
        }
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

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new PreconditionViolationException(field + " is required");
        }
        return value;
    }
}
