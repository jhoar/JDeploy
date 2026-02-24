package com.jdeploy.service;

public class InvariantViolationException extends IllegalStateException {

    public InvariantViolationException(String message) {
        super(message);
    }
}
