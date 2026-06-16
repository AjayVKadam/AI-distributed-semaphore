package com.example.dlock.semaphore;

/**
 * An opaque token representing one permit held against a distributed semaphore.
 *
 * <p>A semaphore with {@code N} permits is modelled as {@code N} independent slots, each
 * backed by its own distributed lock. Holding a permit means holding the lock for one
 * specific slot; the permit records which slot so it can be released or renewed later.</p>
 *
 * @param semaphoreName the logical semaphore this permit belongs to
 * @param slot the zero-based index of the slot held, in {@code [0, permits)}
 * @param lockName the underlying lock name backing the slot (an implementation detail
 *                 exposed only so callers can pass the token back)
 */
public record SemaphorePermit(String semaphoreName, int slot, String lockName) {
}
