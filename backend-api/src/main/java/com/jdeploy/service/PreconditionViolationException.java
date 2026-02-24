package com.jdeploy.service;

public class PreconditionViolationException extends RuntimeException {

    public PreconditionViolationException(String message) {
        super(message);
    }
}
