package com.jdeploy.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Node;

@Node("KubernetesCluster")
public class KubernetesCluster {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    KubernetesCluster() {
    }

    public KubernetesCluster(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
