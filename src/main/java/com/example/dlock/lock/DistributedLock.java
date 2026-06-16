package com.example.dlock.lock;

import java.util.function.Supplier;

/**
 * A mutually exclusive lock shared by every instance of the application through MongoDB.
 *
 * <p>At most one instance can hold a given named lock at any time. Acquisition is granted
 * for a bounded lease (15 minutes by default); if the holder crashes without releasing,
 * the lease eventually expires and another instance may take over, so the lock is
 * self-healing and never permanently stuck.</p>
 *
 * @see MongoDistributedLock
 * @see com.example.dlock.semaphore.DistributedSemaphore
 */
public interface DistributedLock {

    /**
     * Attempts to acquire the lock exactly once, without blocking.
     *
     * @param name the lock name
     * @return {@code true} if this instance now holds the lock, {@code false} if another
     *         instance currently holds a non-expired lease
     */
    boolean tryAcquire(String name);

    /**
     * Attempts to acquire the lock, blocking and retrying until it succeeds or the
     * configured acquire timeout elapses.
     *
     * <p>By default this retries every 15 seconds for up to 3 minutes.</p>
     *
     * @param name the lock name
     * @return {@code true} if the lock was acquired within the timeout, {@code false}
     *         otherwise
     * @throws LockInterruptedException if the calling thread is interrupted while waiting
     */
    boolean acquire(String name);

    /**
     * Extends the lease on a lock already held by this instance.
     *
     * @param name the lock name
     * @return {@code true} if the lease was extended, {@code false} if this instance no
     *         longer owns the lock (for example because the lease had already expired and
     *         was taken over)
     */
    boolean renew(String name);

    /**
     * Releases the lock if and only if it is currently owned by this instance.
     *
     * <p>Releasing a lock not owned by this instance is a no-op, which prevents an
     * instance from accidentally releasing a lease that another instance took over after
     * expiry.</p>
     *
     * @param name the lock name
     */
    void release(String name);

    /**
     * Reports whether this instance currently holds a non-expired lease on the lock.
     *
     * @param name the lock name
     * @return {@code true} if this instance owns the lock right now
     */
    boolean isHeldByCurrentInstance(String name);

    /**
     * Runs the given action while holding the lock, releasing it afterwards.
     *
     * <p>Blocks to acquire the lock (subject to the configured timeout). If the lock
     * cannot be acquired the action is not run and {@code false} is returned.</p>
     *
     * @param name the lock name
     * @param action the action to run while the lock is held
     * @return {@code true} if the lock was acquired and the action executed, {@code false}
     *         if the lock could not be acquired within the timeout
     */
    boolean runWhenLocked(String name, Runnable action);

    /**
     * Computes a value while holding the lock, releasing it afterwards.
     *
     * @param name the lock name
     * @param action the supplier invoked while the lock is held
     * @param <T> the result type
     * @return the value produced by {@code action}
     * @throws LockAcquisitionException if the lock cannot be acquired within the timeout
     */
    <T> T callWhenLocked(String name, Supplier<T> action);
}
