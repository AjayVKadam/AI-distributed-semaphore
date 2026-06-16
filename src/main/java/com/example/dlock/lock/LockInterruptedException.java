package com.example.dlock.lock;

/**
 * Thrown when a thread is interrupted while blocking to acquire a lock or semaphore
 * permit. The interrupt status of the thread is restored before this exception is thrown.
 */
public class LockInterruptedException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param name the lock or semaphore name being waited on
     * @param cause the originating {@link InterruptedException}
     */
    public LockInterruptedException(String name, Throwable cause) {
        super("Interrupted while waiting to acquire '" + name + "'", cause);
    }
}
