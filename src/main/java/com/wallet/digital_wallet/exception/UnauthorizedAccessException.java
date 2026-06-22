package com.wallet.digital_wallet.exception;

/**
 * Thrown when an authenticated user tries to access or modify a resource
 * they do not own (and are not an admin). Distinct from "not authenticated"
 * (401) - this represents "I know who you are, but you can't do this" (403).
 */
public class UnauthorizedAccessException extends RuntimeException {
    public UnauthorizedAccessException(String message) {
        super(message);
    }
}