package com.example.dlock.lock;

/**
 * Thrown when a lock could not be acquired within the configured timeout for an
 * operation that requires the lock in order to proceed.
 */
public class LockAcquisitionException extends RuntimeException {

    /**
     * Creates the exception for a named lock that could not be acquired.
     *
     * @param lockName the lock that could not be acquired
     */
    public LockAcquisitionException(String lockName) {
        super("Could not acquire lock '" + lockName + "' within the configured timeout");
    }
}
