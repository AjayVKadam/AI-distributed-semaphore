package com.example.dlock.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the distributed lock and semaphore.
 *
 * <p>Bound from the {@code distributed-lock.*} keys in {@code application.yaml}.
 * Durations accept ISO-8601 ({@code PT15M}) or Spring's shorthand ({@code 15m}).</p>
 *
 * @param leaseDuration how long a single successful acquisition remains valid before
 *                      it is treated as expired and may be taken over by another
 *                      instance; defaults to 15 minutes
 * @param acquireTimeout maximum time a blocking acquire keeps retrying before giving
 *                       up; defaults to 3 minutes
 * @param retryInterval delay between successive acquisition attempts while blocking;
 *                      defaults to 15 seconds
 */
@ConfigurationProperties(prefix = "distributed-lock")
public record DistributedLockProperties(
        Duration leaseDuration,
        Duration acquireTimeout,
        Duration retryInterval) {

    /**
     * Applies defaults for any value that was not supplied in configuration.
     *
     * @param leaseDuration configured lease duration, or {@code null} for the default
     * @param acquireTimeout configured acquire timeout, or {@code null} for the default
     * @param retryInterval configured retry interval, or {@code null} for the default
     */
    public DistributedLockProperties {
        if (leaseDuration == null) {
            leaseDuration = Duration.ofMinutes(15);
        }
        if (acquireTimeout == null) {
            acquireTimeout = Duration.ofMinutes(3);
        }
        if (retryInterval == null) {
            retryInterval = Duration.ofSeconds(15);
        }
    }
}
