package com.jdeploy.domain;

import com.jdeploy.service.PostconditionViolationException;
import com.jdeploy.service.PreconditionViolationException;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Node;


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
        this.type = requireNonNull(type, "type");
        if (this.name.isBlank() || this.type == null) {
            throw new PostconditionViolationException("ExecutionEnvironment construction failed to initialize required state");
        }
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
