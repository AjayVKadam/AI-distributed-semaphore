package com.example.dlock.lock;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dlock.config.DistributedLockProperties;
import java.time.Duration;
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
 * Verifies the core mutual-exclusion guarantee of {@link MongoDistributedLock} against a
 * real MongoDB instance running in Testcontainers. Two locks sharing one collection but
 * with distinct instance ids simulate two pods competing for the same lock.
 */
@Testcontainers
@SpringBootTest
class MongoDistributedLockIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Autowired
    MongoTemplate mongoTemplate;

    DistributedLock podA;
    DistributedLock podB;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(LockDocument.class);
        // Short lease so the expiry/takeover path can be exercised quickly.
        DistributedLockProperties fastProps = new DistributedLockProperties(
                Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofMillis(100));
        podA = new MongoDistributedLock(mongoTemplate, fastProps, "pod-A");
        podB = new MongoDistributedLock(mongoTemplate, fastProps, "pod-B");
    }

    @Test
    void onlyOneInstanceCanHoldTheLock() {
        assertThat(podA.tryAcquire("orders")).isTrue();
        assertThat(podB.tryAcquire("orders")).isFalse();

        assertThat(podA.isHeldByCurrentInstance("orders")).isTrue();
        assertThat(podB.isHeldByCurrentInstance("orders")).isFalse();
    }

    @Test
    void lockBecomesAvailableAfterRelease() {
        assertThat(podA.tryAcquire("orders")).isTrue();
        podA.release("orders");

        assertThat(podB.tryAcquire("orders")).isTrue();
        assertThat(podA.tryAcquire("orders")).isFalse();
    }

    @Test
    void releaseByNonOwnerIsIgnored() {
        assertThat(podA.tryAcquire("orders")).isTrue();
        // pod-B does not own the lock; its release must not free pod-A's lease.
        podB.release("orders");
        assertThat(podA.isHeldByCurrentInstance("orders")).isTrue();
    }

    @Test
    void expiredLeaseCanBeTakenOverByAnotherInstance() throws InterruptedException {
        assertThat(podA.tryAcquire("orders")).isTrue();
        // Wait for the 2s lease to expire.
        Thread.sleep(2_500);
        assertThat(podB.tryAcquire("orders")).isTrue();
        assertThat(podA.isHeldByCurrentInstance("orders")).isFalse();
    }

    @Test
    void ownerCanRenewButFormerOwnerCannotAfterTakeover() throws InterruptedException {
        assertThat(podA.tryAcquire("orders")).isTrue();
        assertThat(podA.renew("orders")).isTrue();

        Thread.sleep(2_500);
        assertThat(podB.tryAcquire("orders")).isTrue();
        // pod-A's lease was taken over; it can no longer renew.
        assertThat(podA.renew("orders")).isFalse();
    }
}
