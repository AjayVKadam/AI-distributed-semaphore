package com.example.dlock.semaphore;

import java.util.Optional;

/**
 * A counting semaphore shared by every instance of the application through MongoDB.
 *
 * <p>Whereas a {@link com.example.dlock.lock.DistributedLock} admits a single holder, a
 * semaphore admits up to {@code permits} holders concurrently across all instances and
 * data centers. It is a direct extension of the distributed lock: a semaphore with
 * {@code N} permits is just {@code N} named locks ("slots"), and acquiring a permit means
 * winning any one free slot. Each permit inherits the lock's bounded lease, so permits
 * held by a crashed instance are automatically reclaimed when their lease expires.</p>
 *
 * @see SemaphorePermit
 * @see com.example.dlock.lock.DistributedLock
 */
public interface DistributedSemaphore {

    /**
     * Attempts to acquire a single permit exactly once, without blocking.
     *
     * @param name the semaphore name
     * @param permits the total number of permits the semaphore allows (its capacity);
     *                must be at least 1 and must be the same for every caller of this
     *                semaphore
     * @return a permit token if one was acquired, or {@link Optional#empty()} if all
     *         permits are currently taken
     * @throws IllegalArgumentException if {@code permits} is less than 1
     */
    Optional<SemaphorePermit> tryAcquire(String name, int permits);

    /**
     * Attempts to acquire a single permit, blocking and retrying until one becomes
     * available or the configured acquire timeout elapses.
     *
     * <p>By default this retries every 15 seconds for up to 3 minutes.</p>
     *
     * @param name the semaphore name
     * @param permits the total capacity of the semaphore (see {@link #tryAcquire})
     * @return a permit token if one was acquired within the timeout, or
     *         {@link Optional#empty()} otherwise
     * @throws IllegalArgumentException if {@code permits} is less than 1
     */
    Optional<SemaphorePermit> acquire(String name, int permits);

    /**
     * Extends the lease on a permit currently held by this instance.
     *
     * @param permit the permit to renew
     * @return {@code true} if the lease was extended, {@code false} if this instance no
     *         longer holds the permit
     */
    boolean renew(SemaphorePermit permit);

    /**
     * Releases a previously acquired permit. Releasing a permit not held by this instance
     * is a no-op.
     *
     * @param permit the permit to release
     */
    void release(SemaphorePermit permit);
}
