package com.example.dlock.lock;

import com.example.dlock.config.DistributedLockProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * MongoDB-backed implementation of {@link DistributedLock}.
 *
 * <p><strong>How mutual exclusion is guaranteed.</strong> Each lock is a single document
 * keyed by the lock name ({@code _id}). Acquisition uses one atomic
 * {@code findAndModify} with {@code upsert = true} whose query matches only a
 * <em>free</em> lock, i.e. one whose lease has already expired:</p>
 *
 * <pre>{@code
 * query  : { _id: name, expiresAt: { $lt: now } }
 * update : { $set: { owner, acquiredAt, expiresAt: now + lease } }
 * upsert : true
 * }</pre>
 *
 * <p>There are exactly three cases, all resolved atomically by the server:</p>
 * <ul>
 *   <li><b>No document exists</b> &rarr; the query matches nothing, so the upsert inserts
 *       a new document and this instance wins the lock.</li>
 *   <li><b>An expired document exists</b> &rarr; the query matches it and the update
 *       re-stamps it with this instance as owner; this instance wins the lock.</li>
 *   <li><b>A live (non-expired) document exists</b> &rarr; the query matches nothing, so
 *       the upsert tries to <em>insert</em> a second document with the same {@code _id}
 *       and MongoDB rejects it with a duplicate-key error. That error is the signal that
 *       the lock is currently held, and acquisition returns {@code false}.</li>
 * </ul>
 *
 * <p>Because the unique {@code _id} index serializes the insert, no two instances can
 * ever observe the lock as free at the same instant, even across data centers.</p>
 */
@Component
public class MongoDistributedLock implements DistributedLock {

    private static final Logger log = LoggerFactory.getLogger(MongoDistributedLock.class);

    private final MongoTemplate mongoTemplate;
    private final DistributedLockProperties properties;
    private final String instanceId;

    /**
     * Creates the lock.
     *
     * @param mongoTemplate template used for the atomic MongoDB operations
     * @param properties lease, timeout and retry-interval configuration
     * @param instanceId identifier recorded as the owner of locks held by this instance
     */
    public MongoDistributedLock(MongoTemplate mongoTemplate,
                                DistributedLockProperties properties,
                                String instanceId) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
        this.instanceId = instanceId;
    }

    @Override
    public boolean tryAcquire(String name) {
        log.debug("entry: tryAcquire(name={})", name);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.leaseDuration());

        Query freeLock = Query.query(Criteria.where("_id").is(name)
                .and("expiresAt").lt(now));
        Update grant = new Update()
                .set("owner", instanceId)
                .set("acquiredAt", now)
                .set("expiresAt", expiresAt);
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);

        try {
            mongoTemplate.findAndModify(freeLock, grant, options, LockDocument.class);
            log.debug("exit: tryAcquire(name={}) -> true (owner={}, expiresAt={})",
                    name, instanceId, expiresAt);
            return true;
        } catch (DuplicateKeyException held) {
            // A live document already exists: the lock is held by another instance.
            log.debug("exit: tryAcquire(name={}) -> false (held by another instance)", name);
            return false;
        }
    }

    @Override
    public boolean acquire(String name) {
        log.debug("entry: acquire(name={})", name);
        Duration timeout = properties.acquireTimeout();
        Duration retryInterval = properties.retryInterval();
        Instant deadline = Instant.now().plus(timeout);

        int attempt = 0;
        while (true) {
            attempt++;
            if (tryAcquire(name)) {
                log.debug("exit: acquire(name={}) -> true (attempt={})", name, attempt);
                return true;
            }
            if (Instant.now().plus(retryInterval).isAfter(deadline)) {
                log.warn("Gave up acquiring lock '{}' after {} (attempts={})",
                        name, timeout, attempt);
                log.debug("exit: acquire(name={}) -> false", name);
                return false;
            }
            log.debug("Lock '{}' busy, retrying in {} (attempt={})", name, retryInterval, attempt);
            sleep(name, retryInterval);
        }
    }

    @Override
    public boolean renew(String name) {
        log.debug("entry: renew(name={})", name);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.leaseDuration());

        Query ownedAndLive = Query.query(Criteria.where("_id").is(name)
                .and("owner").is(instanceId)
                .and("expiresAt").gt(now));
        Update extend = new Update().set("expiresAt", expiresAt);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);

        LockDocument renewed = mongoTemplate.findAndModify(ownedAndLive, extend, options, LockDocument.class);
        boolean ok = renewed != null;
        if (ok) {
            log.debug("exit: renew(name={}) -> true (expiresAt={})", name, expiresAt);
        } else {
            log.warn("Could not renew lock '{}': no longer owned by this instance", name);
            log.debug("exit: renew(name={}) -> false", name);
        }
        return ok;
    }

    @Override
    public void release(String name) {
        log.debug("entry: release(name={})", name);
        Query ownedByMe = Query.query(Criteria.where("_id").is(name).and("owner").is(instanceId));
        long removed = mongoTemplate.remove(ownedByMe, LockDocument.class).getDeletedCount();
        if (removed > 0) {
            log.debug("exit: release(name={}) -> released", name);
        } else {
            log.debug("exit: release(name={}) -> nothing to release (not owned by this instance)", name);
        }
    }

    @Override
    public boolean isHeldByCurrentInstance(String name) {
        log.debug("entry: isHeldByCurrentInstance(name={})", name);
        Query ownedAndLive = Query.query(Criteria.where("_id").is(name)
                .and("owner").is(instanceId)
                .and("expiresAt").gt(Instant.now()));
        boolean held = mongoTemplate.exists(ownedAndLive, LockDocument.class);
        log.debug("exit: isHeldByCurrentInstance(name={}) -> {}", name, held);
        return held;
    }

    @Override
    public boolean runWhenLocked(String name, Runnable action) {
        log.debug("entry: runWhenLocked(name={})", name);
        if (!acquire(name)) {
            log.debug("exit: runWhenLocked(name={}) -> false (not acquired)", name);
            return false;
        }
        try {
            action.run();
            log.debug("exit: runWhenLocked(name={}) -> true", name);
            return true;
        } finally {
            release(name);
        }
    }

    @Override
    public <T> T callWhenLocked(String name, Supplier<T> action) {
        log.debug("entry: callWhenLocked(name={})", name);
        if (!acquire(name)) {
            log.error("callWhenLocked failed: lock '{}' not acquired within timeout", name);
            throw new LockAcquisitionException(name);
        }
        try {
            T result = action.get();
            log.debug("exit: callWhenLocked(name={}) -> computed result", name);
            return result;
        } finally {
            release(name);
        }
    }

    /**
     * Sleeps for the retry interval, converting interruption into a runtime exception
     * while preserving the thread's interrupt status.
     *
     * @param name the lock name (for diagnostics)
     * @param interval how long to sleep
     */
    private void sleep(String name, Duration interval) {
        try {
            Thread.sleep(interval.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting to acquire lock '{}'", name, ex);
            throw new LockInterruptedException(name, ex);
        }
    }
}
