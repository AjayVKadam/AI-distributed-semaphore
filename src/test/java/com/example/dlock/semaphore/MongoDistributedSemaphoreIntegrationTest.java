package com.example.dlock.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dlock.config.DistributedLockProperties;
import com.example.dlock.lock.LockDocument;
import com.example.dlock.lock.MongoDistributedLock;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that {@link MongoDistributedSemaphore} admits at most {@code permits} holders
 * concurrently across instances, using a real MongoDB instance in Testcontainers.
 */
@Testcontainers
@SpringBootTest
class MongoDistributedSemaphoreIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Autowired
    MongoTemplate mongoTemplate;

    private DistributedSemaphore newInstance(String instanceId, DistributedLockProperties props) {
        return new MongoDistributedSemaphore(new MongoDistributedLock(mongoTemplate, props, instanceId), props);
    }

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(LockDocument.class);
    }

    @Test
    void admitsUpToCapacityThenBlocks() {
        DistributedLockProperties props = new DistributedLockProperties(
                Duration.ofMinutes(15), Duration.ofMinutes(3), Duration.ofSeconds(15));
        DistributedSemaphore a = newInstance("pod-A", props);
        DistributedSemaphore b = newInstance("pod-B", props);
        DistributedSemaphore c = newInstance("pod-C", props);

        int capacity = 2;
        Optional<SemaphorePermit> p1 = a.tryAcquire("downloads", capacity);
        Optional<SemaphorePermit> p2 = b.tryAcquire("downloads", capacity);
        Optional<SemaphorePermit> p3 = c.tryAcquire("downloads", capacity);

        assertThat(p1).isPresent();
        assertThat(p2).isPresent();
        // Capacity exhausted: the third request gets no permit.
        assertThat(p3).isEmpty();
        // The two granted permits occupy distinct slots.
        assertThat(p1.get().slot()).isNotEqualTo(p2.get().slot());
    }

    @Test
    void releasingAPermitLetsAnotherInstanceIn() {
        DistributedLockProperties props = new DistributedLockProperties(
                Duration.ofMinutes(15), Duration.ofMinutes(3), Duration.ofSeconds(15));
        DistributedSemaphore a = newInstance("pod-A", props);
        DistributedSemaphore b = newInstance("pod-B", props);

        int capacity = 1;
        Optional<SemaphorePermit> held = a.tryAcquire("single", capacity);
        assertThat(held).isPresent();
        assertThat(b.tryAcquire("single", capacity)).isEmpty();

        a.release(held.get());
        assertThat(b.tryAcquire("single", capacity)).isPresent();
    }

    @Test
    void invalidCapacityIsRejected() {
        DistributedLockProperties props = new DistributedLockProperties(null, null, null);
        DistributedSemaphore a = newInstance("pod-A", props);
        try {
            a.tryAcquire("bad", 0);
            assertThat(false).as("expected IllegalArgumentException").isTrue();
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageContaining("permits must be >= 1");
        }
    }
}
