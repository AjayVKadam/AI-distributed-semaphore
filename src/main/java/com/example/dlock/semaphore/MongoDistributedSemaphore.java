package com.example.dlock.semaphore;

import com.example.dlock.config.DistributedLockProperties;
import com.example.dlock.lock.DistributedLock;
import com.example.dlock.lock.LockInterruptedException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link DistributedSemaphore} layered directly on top of
 * {@link DistributedLock}.
 *
 * <p>A semaphore named {@code "downloads"} with capacity {@code 3} is represented by the
 * three slot locks {@code "downloads#0"}, {@code "downloads#1"} and {@code "downloads#2"}.
 * Acquiring a permit means winning any one of those slot locks; releasing returns it.
 * Because each slot is a fully independent atomic lock, the number of simultaneously held
 * permits can never exceed the capacity, and a slot whose holder dies is freed when its
 * lease expires.</p>
 *
 * <p>Slots are probed in a randomized order on each attempt to spread contending
 * instances across free slots and reduce the chance that many instances collide on the
 * same slot.</p>
 */
@Component
public class MongoDistributedSemaphore implements DistributedSemaphore {

    private static final Logger log = LoggerFactory.getLogger(MongoDistributedSemaphore.class);

    private final DistributedLock lock;
    private final DistributedLockProperties properties;

    /**
     * Creates the semaphore.
     *
     * @param lock the distributed lock used to back each permit slot
     * @param properties timeout and retry-interval configuration for blocking acquire
     */
    public MongoDistributedSemaphore(DistributedLock lock, DistributedLockProperties properties) {
        this.lock = lock;
        this.properties = properties;
    }

    @Override
    public Optional<SemaphorePermit> tryAcquire(String name, int permits) {
        log.debug("entry: tryAcquire(name={}, permits={})", name, permits);
        validatePermits(permits);

        for (int slot : shuffledSlots(permits)) {
            String slotLock = slotLockName(name, slot);
            if (lock.tryAcquire(slotLock)) {
                SemaphorePermit permit = new SemaphorePermit(name, slot, slotLock);
                log.debug("exit: tryAcquire(name={}) -> acquired slot {}", name, slot);
                return Optional.of(permit);
            }
        }
        log.debug("exit: tryAcquire(name={}) -> empty (all {} permits taken)", name, permits);
        return Optional.empty();
    }

    @Override
    public Optional<SemaphorePermit> acquire(String name, int permits) {
        log.debug("entry: acquire(name={}, permits={})", name, permits);
        validatePermits(permits);

        Duration timeout = properties.acquireTimeout();
        Duration retryInterval = properties.retryInterval();
        Instant deadline = Instant.now().plus(timeout);

        int attempt = 0;
        while (true) {
            attempt++;
            Optional<SemaphorePermit> permit = tryAcquire(name, permits);
            if (permit.isPresent()) {
                log.debug("exit: acquire(name={}) -> acquired slot {} (attempt={})",
                        name, permit.get().slot(), attempt);
                return permit;
            }
            if (Instant.now().plus(retryInterval).isAfter(deadline)) {
                log.warn("Gave up acquiring a permit on semaphore '{}' after {} (attempts={})",
                        name, timeout, attempt);
                log.debug("exit: acquire(name={}) -> empty", name);
                return Optional.empty();
            }
            log.debug("Semaphore '{}' full, retrying in {} (attempt={})", name, retryInterval, attempt);
            sleep(name, retryInterval);
        }
    }

    @Override
    public boolean renew(SemaphorePermit permit) {
        log.debug("entry: renew(permit={})", permit);
        boolean ok = lock.renew(permit.lockName());
        log.debug("exit: renew(permit={}) -> {}", permit, ok);
        return ok;
    }

    @Override
    public void release(SemaphorePermit permit) {
        log.debug("entry: release(permit={})", permit);
        lock.release(permit.lockName());
        log.debug("exit: release(permit={})", permit);
    }

    /**
     * Builds the underlying lock name for a given semaphore slot.
     *
     * @param name the semaphore name
     * @param slot the slot index
     * @return the slot's backing lock name, e.g. {@code "downloads#2"}
     */
    private String slotLockName(String name, int slot) {
        return name + "#" + slot;
    }

    /**
     * Produces the slot indices {@code [0, permits)} in a random order.
     *
     * @param permits the semaphore capacity
     * @return a shuffled list of slot indices
     */
    private List<Integer> shuffledSlots(int permits) {
        List<Integer> slots = new ArrayList<>(permits);
        for (int i = 0; i < permits; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots);
        return slots;
    }

    /**
     * Validates the requested capacity.
     *
     * @param permits the semaphore capacity
     * @throws IllegalArgumentException if {@code permits} is less than 1
     */
    private void validatePermits(int permits) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1 but was " + permits);
        }
    }

    /**
     * Sleeps for the retry interval, converting interruption into a runtime exception
     * while preserving the thread's interrupt status.
     *
     * @param name the semaphore name (for diagnostics)
     * @param interval how long to sleep
     */
    private void sleep(String name, Duration interval) {
        try {
            Thread.sleep(interval.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting to acquire a permit on semaphore '{}'", name, ex);
            throw new LockInterruptedException(name, ex);
        }
    }
}
