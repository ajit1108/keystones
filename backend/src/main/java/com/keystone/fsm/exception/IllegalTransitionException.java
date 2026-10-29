package com.keystone.fsm.exception;

/**
 * Raised when a work-order status transition is not allowed by the lifecycle.
 * Mapped to HTTP 409 Conflict.
 */
public class IllegalTransitionException extends RuntimeException {
    public IllegalTransitionException(String message) {
        super(message);
    }
}
