package com.keystone.fsm.exception;

/**
 * Raised when a request conflicts with the current state of a resource
 * (for example consuming more stock than is available). Mapped to HTTP 409.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
