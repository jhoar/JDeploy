package com.jdeploy.service;

public class PostconditionViolationException extends IllegalStateException {

    public PostconditionViolationException(String message) {
        super(message);
    }
}
