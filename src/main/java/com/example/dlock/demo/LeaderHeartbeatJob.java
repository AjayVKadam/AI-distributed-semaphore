package com.example.dlock.demo;

import com.example.dlock.lock.DistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Demonstrates single-leader behavior across pods using the distributed lock.
 *
 * <p>Every pod runs this job on the same schedule. On each tick a pod tries to acquire
 * the shared {@code "leader-election"} lock: whichever pod currently holds the 15-minute
 * lease renews it and acts as leader, while the others simply observe that the lock is
 * taken. If the leader pod dies, its lease expires and another pod takes over on a
 * subsequent tick. This makes it easy to confirm, from the logs across all pods, that
 * exactly one instance holds the lock at any time.</p>
 */
@Component
public class LeaderHeartbeatJob {

    private static final Logger log = LoggerFactory.getLogger(LeaderHeartbeatJob.class);

    /** Name of the lock used for the single-leader demonstration. */
    private static final String LEADER_LOCK = "leader-election";

    private final DistributedLock lock;

    /**
     * Creates the job.
     *
     * @param lock the distributed lock used for leader election
     */
    public LeaderHeartbeatJob(DistributedLock lock) {
        this.lock = lock;
    }

    /**
     * Runs every 30 seconds to claim or renew leadership.
     *
     * <p>Uses {@code tryAcquire} (non-blocking) so non-leaders return immediately rather
     * than waiting; the holder renews its lease to retain leadership.</p>
     */
    @Scheduled(fixedDelayString = "PT30S")
    public void heartbeat() {
        log.debug("entry: heartbeat");
        if (lock.isHeldByCurrentInstance(LEADER_LOCK)) {
            boolean renewed = lock.renew(LEADER_LOCK);
            log.info("I am the leader (renewed lease: {})", renewed);
        } else if (lock.tryAcquire(LEADER_LOCK)) {
            log.info("Became the leader");
        } else {
            log.info("Another instance is the leader; standing by");
        }
        log.debug("exit: heartbeat");
    }
}
