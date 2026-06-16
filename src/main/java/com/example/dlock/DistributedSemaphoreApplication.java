package com.example.dlock;

import com.example.dlock.config.DistributedLockProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the distributed lock / semaphore service.
 *
 * <p>The application is designed to run as several identical replicas (Kubernetes
 * pods), potentially spread across multiple data centers, all pointing at the same
 * MongoDB deployment. Coordination between the replicas is achieved purely through
 * atomic MongoDB operations, so no replica needs to know about any other replica.</p>
 *
 * @see com.example.dlock.lock.DistributedLock
 * @see com.example.dlock.semaphore.DistributedSemaphore
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(DistributedLockProperties.class)
public class DistributedSemaphoreApplication {

    private static final Logger log = LoggerFactory.getLogger(DistributedSemaphoreApplication.class);

    /**
     * Boots the Spring application context.
     *
     * @param args standard command line arguments forwarded to Spring Boot
     */
    public static void main(String[] args) {
        log.debug("entry: main(args.length={})", args.length);
        SpringApplication.run(DistributedSemaphoreApplication.class, args);
        log.debug("exit: main");
    }
}
