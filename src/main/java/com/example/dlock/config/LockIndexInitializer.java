package com.example.dlock.config;

import com.example.dlock.lock.LockDocument;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Ensures the supporting MongoDB index exists for the lock collection.
 *
 * <p>A TTL index on {@link LockDocument#getExpiresAt()} with {@code expireAfterSeconds = 0}
 * lets MongoDB reclaim documents whose lease has elapsed. This is purely housekeeping:
 * the acquisition logic already treats an expired document as free, so correctness does
 * not depend on the background TTL monitor (which only runs about once per minute).</p>
 */
@Component
public class LockIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(LockIndexInitializer.class);

    private final MongoTemplate mongoTemplate;

    /**
     * Creates the initializer.
     *
     * @param mongoTemplate template used to manage indexes on the lock collection
     */
    public LockIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Creates the TTL index once the application context is fully started.
     *
     * @param event the application-ready event (unused, present to bind the listener)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes(ApplicationReadyEvent event) {
        log.debug("entry: ensureIndexes");
        try {
            mongoTemplate.indexOps(LockDocument.class)
                    .createIndex(new Index().on("expiresAt", org.springframework.data.domain.Sort.Direction.ASC)
                            .expire(0, TimeUnit.SECONDS)
                            .named("lock_ttl_expiresAt"));
            log.info("Ensured TTL index 'lock_ttl_expiresAt' on collection '{}'",
                    mongoTemplate.getCollectionName(LockDocument.class));
        } catch (RuntimeException ex) {
            // Index creation must never prevent the service from starting; the lease
            // semantics remain correct without it.
            log.error("Failed to ensure TTL index on lock collection", ex);
        }
        log.debug("exit: ensureIndexes");
    }
}
