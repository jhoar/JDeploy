package com.jdeploy.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Node;

@Node("GridCluster")
public class GridCluster {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    GridCluster() {
    }

    public GridCluster(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
