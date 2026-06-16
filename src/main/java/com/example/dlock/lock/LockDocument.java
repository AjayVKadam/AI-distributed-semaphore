package com.example.dlock.lock;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document representing a single named lock.
 *
 * <p>The document {@code _id} is the lock name, so the collection can hold at most one
 * document per lock. Holding the lock means there is a document whose {@code expiresAt}
 * is in the future; an absent or expired document means the lock is free. All state
 * transitions are performed with atomic {@code findAndModify}/upsert operations in
 * {@link MongoDistributedLock}.</p>
 */
@Document(collection = "distributed_locks")
public class LockDocument {

    /** Lock name; also the MongoDB {@code _id}, which guarantees uniqueness per lock. */
    @Id
    private String name;

    /** Identifier of the instance currently holding the lock. */
    private String owner;

    /** Instant at which the current holder acquired (or last renewed) the lock. */
    private Instant acquiredAt;

    /** Instant after which the lease is considered expired and the lock free. */
    private Instant expiresAt;

    /** Creates an empty document (required by the MongoDB mapping layer). */
    public LockDocument() {
    }

    /**
     * Creates a fully populated lock document.
     *
     * @param name the lock name / document id
     * @param owner the owning instance id
     * @param acquiredAt when the lock was acquired
     * @param expiresAt when the lease expires
     */
    public LockDocument(String name, String owner, Instant acquiredAt, Instant expiresAt) {
        this.name = name;
        this.owner = owner;
        this.acquiredAt = acquiredAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the lock name.
     *
     * @return the lock name / document id
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the lock name.
     *
     * @param name the lock name / document id
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the owning instance id.
     *
     * @return the current owner, or {@code null} if never set
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Sets the owning instance id.
     *
     * @param owner the owning instance id
     */
    public void setOwner(String owner) {
        this.owner = owner;
    }

    /**
     * Returns the acquisition instant.
     *
     * @return when the lock was acquired or last renewed
     */
    public Instant getAcquiredAt() {
        return acquiredAt;
    }

    /**
     * Sets the acquisition instant.
     *
     * @param acquiredAt when the lock was acquired or last renewed
     */
    public void setAcquiredAt(Instant acquiredAt) {
        this.acquiredAt = acquiredAt;
    }

    /**
     * Returns the lease expiry instant.
     *
     * @return when the lease expires
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets the lease expiry instant.
     *
     * @param expiresAt when the lease expires
     */
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public String toString() {
        return "LockDocument{name='" + name + "', owner='" + owner
                + "', acquiredAt=" + acquiredAt + ", expiresAt=" + expiresAt + '}';
    }
}
