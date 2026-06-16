package com.example.dlock.config;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies a stable identifier that uniquely names this running instance.
 *
 * <p>The identifier is recorded as the owner of every lock and semaphore permit this
 * instance holds, which lets ownership be verified on renewal and release. In
 * Kubernetes the pod name (exposed through the downward API as {@code POD_NAME} or the
 * default {@code HOSTNAME}) is unique and human-readable, so it is preferred; a random
 * UUID suffix guards against accidental reuse if a pod name were ever recycled.</p>
 */
@Configuration(proxyBeanMethods = false)
public class InstanceIdentity {

    private static final Logger log = LoggerFactory.getLogger(InstanceIdentity.class);

    /**
     * Builds the instance identifier bean used as the lock owner value.
     *
     * @param podName the pod / host name resolved from {@code POD_NAME} then
     *                {@code HOSTNAME}, defaulting to {@code "local"} when neither is set
     * @return a process-unique owner identifier of the form {@code <podName>/<uuid>}
     */
    @Bean
    public String instanceId(@Value("${POD_NAME:${HOSTNAME:local}}") String podName) {
        log.debug("entry: instanceId(podName={})", podName);
        String id = podName + "/" + UUID.randomUUID();
        log.info("This instance will own locks under id={}", id);
        log.debug("exit: instanceId -> {}", id);
        return id;
    }
}
