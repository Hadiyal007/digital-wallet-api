package com.wallet.digital_wallet.exception;

/**
 * Thrown when a concurrent modification conflict is detected on a Wallet.
 *
 * This wraps Spring's ObjectOptimisticLockingFailureException (which itself
 * wraps JPA's OptimisticLockException) into a domain-meaningful message
 * that makes sense to an API client, hiding internal JPA/Hibernate details.
 *
 * HTTP mapping: 409 Conflict — the request was valid, but the server's
 * current state conflicts with the request. The client should retry.
 */
public class OptimisticLockException extends RuntimeException {
    public OptimisticLockException(String message) {
        super(message);
    }
}