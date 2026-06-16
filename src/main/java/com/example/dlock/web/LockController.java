package com.example.dlock.web;

import com.example.dlock.lock.DistributedLock;
import com.example.dlock.semaphore.DistributedSemaphore;
import com.example.dlock.semaphore.SemaphorePermit;
import jakarta.validation.constraints.Min;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for exercising the distributed lock and semaphore.
 *
 * <p>Intended primarily for demonstration and integration testing across pods: every
 * response includes the {@code instanceId} of the pod that handled the request, so when
 * the service is scaled out it is easy to observe that only one pod can hold a given lock
 * at a time, while up to {@code permits} pods can hold a semaphore.</p>
 */
@RestController
@RequestMapping("/api")
@Validated
public class LockController {

    private static final Logger log = LoggerFactory.getLogger(LockController.class);

    private final DistributedLock lock;
    private final DistributedSemaphore semaphore;
    private final String instanceId;

    /**
     * Creates the controller.
     *
     * @param lock the distributed lock
     * @param semaphore the distributed semaphore
     * @param instanceId this pod's instance identifier, echoed in every response
     */
    public LockController(DistributedLock lock, DistributedSemaphore semaphore, String instanceId) {
        this.lock = lock;
        this.semaphore = semaphore;
        this.instanceId = instanceId;
    }

    /**
     * Attempts to acquire a lock once, without blocking.
     *
     * @param name the lock name
     * @return {@code 200} with {@code acquired=true} if won, otherwise {@code 409} with
     *         {@code acquired=false}
     */
    @PostMapping("/locks/{name}/try")
    public ResponseEntity<Map<String, Object>> tryLock(@PathVariable String name) {
        log.debug("entry: tryLock(name={})", name);
        boolean acquired = lock.tryAcquire(name);
        ResponseEntity<Map<String, Object>> response = ResponseEntity
                .status(acquired ? HttpStatus.OK : HttpStatus.CONFLICT)
                .body(Map.of("instanceId", instanceId, "lock", name, "acquired", acquired));
        log.debug("exit: tryLock(name={}) -> acquired={}", name, acquired);
        return response;
    }

    /**
     * Attempts to acquire a lock, blocking and retrying up to the configured timeout.
     *
     * @param name the lock name
     * @return {@code 200} if acquired within the timeout, otherwise {@code 408}
     */
    @PostMapping("/locks/{name}/acquire")
    public ResponseEntity<Map<String, Object>> acquireLock(@PathVariable String name) {
        log.debug("entry: acquireLock(name={})", name);
        boolean acquired = lock.acquire(name);
        ResponseEntity<Map<String, Object>> response = ResponseEntity
                .status(acquired ? HttpStatus.OK : HttpStatus.REQUEST_TIMEOUT)
                .body(Map.of("instanceId", instanceId, "lock", name, "acquired", acquired));
        log.debug("exit: acquireLock(name={}) -> acquired={}", name, acquired);
        return response;
    }

    /**
     * Releases a lock if held by this pod.
     *
     * @param name the lock name
     * @return {@code 200} acknowledging the release attempt
     */
    @DeleteMapping("/locks/{name}")
    public ResponseEntity<Map<String, Object>> releaseLock(@PathVariable String name) {
        log.debug("entry: releaseLock(name={})", name);
        lock.release(name);
        log.debug("exit: releaseLock(name={})", name);
        return ResponseEntity.ok(Map.of("instanceId", instanceId, "lock", name, "released", true));
    }

    /**
     * Reports whether this pod currently holds the lock.
     *
     * @param name the lock name
     * @return {@code 200} with {@code held} reflecting current ownership
     */
    @GetMapping("/locks/{name}/held")
    public ResponseEntity<Map<String, Object>> isHeld(@PathVariable String name) {
        log.debug("entry: isHeld(name={})", name);
        boolean held = lock.isHeldByCurrentInstance(name);
        log.debug("exit: isHeld(name={}) -> {}", name, held);
        return ResponseEntity.ok(Map.of("instanceId", instanceId, "lock", name, "held", held));
    }

    /**
     * Attempts to acquire one permit on a semaphore once, without blocking.
     *
     * @param name the semaphore name
     * @param permits the semaphore capacity (must match across all callers)
     * @return {@code 200} with the acquired {@code slot}, or {@code 409} if all permits
     *         are taken
     */
    @PostMapping("/semaphores/{name}/try")
    public ResponseEntity<Map<String, Object>> trySemaphore(@PathVariable String name,
                                                            @RequestParam @Min(1) int permits) {
        log.debug("entry: trySemaphore(name={}, permits={})", name, permits);
        Optional<SemaphorePermit> permit = semaphore.tryAcquire(name, permits);
        ResponseEntity<Map<String, Object>> response = permit
                .map(p -> ResponseEntity.ok(Map.<String, Object>of(
                        "instanceId", instanceId, "semaphore", name, "acquired", true, "slot", p.slot())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).body(Map.<String, Object>of(
                        "instanceId", instanceId, "semaphore", name, "acquired", false)));
        log.debug("exit: trySemaphore(name={}) -> acquired={}", name, permit.isPresent());
        return response;
    }

    /**
     * Releases a semaphore permit previously acquired by this pod.
     *
     * @param name the semaphore name
     * @param slot the slot index returned when the permit was acquired
     * @return {@code 200} acknowledging the release
     */
    @DeleteMapping("/semaphores/{name}/permits/{slot}")
    public ResponseEntity<Map<String, Object>> releaseSemaphore(@PathVariable String name,
                                                               @PathVariable int slot) {
        log.debug("entry: releaseSemaphore(name={}, slot={})", name, slot);
        semaphore.release(new SemaphorePermit(name, slot, name + "#" + slot));
        log.debug("exit: releaseSemaphore(name={}, slot={})", name, slot);
        return ResponseEntity.ok(Map.of("instanceId", instanceId, "semaphore", name, "slot", slot, "released", true));
    }
}
