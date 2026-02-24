package com.jdeploy.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.Objects;

@Node("ExecutionEnvironment")
public class ExecutionEnvironment {

    public enum EnvironmentType {
        DEVELOPMENT,
        TEST,
        STAGING,
        PRODUCTION
    }

    @Id
    @GeneratedValue
    private Long id;

    private String name;
    private EnvironmentType type;

    ExecutionEnvironment() {
    }

    public ExecutionEnvironment(String name, EnvironmentType type) {
        this.name = requireNonBlank(name, "name");
        this.type = Objects.requireNonNull(type, "type must not be null");
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public EnvironmentType getType() {
        return type;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
