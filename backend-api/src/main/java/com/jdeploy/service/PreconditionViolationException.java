package com.jdeploy.service;

public class PreconditionViolationException extends IllegalArgumentException {

    public PreconditionViolationException(String message) {
        super(message);
    }
}
