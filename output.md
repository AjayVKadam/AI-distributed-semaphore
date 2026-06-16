# Build Output Summary

A Spring Boot 4.1.0 / Java 25 service (Gradle Groovy DSL, wrapper pinned to **9.5.1** —
needed because 8.14.3 can't *run* on Java 25), implementing a MongoDB-backed distributed
lock and a semaphore extending it.

## What was built (against each goal in `docs/request_prompt.md`)

### Distributed lock — atomic, GA MongoDB (`MongoDistributedLock.java`)
Mutual exclusion comes from a single atomic `findAndModify` + `upsert`:
- Query matches only a *free* lock: `{ _id: name, expiresAt: { $lt: now } }`
- No doc → upsert inserts (won). Expired doc → re-stamped (won). **Live doc → upsert tries
  a duplicate `_id` insert → `DuplicateKeyException` → not acquired.**

The unique `_id` index serializes the insert, so two pods can never both win — even across
data centers. Lease is **15 minutes**.

### Wait / retry
Blocking `acquire()` retries **every 15s for up to 3m**, then gives up (all configurable in
`application.yaml` / via env).

### Semaphore (`MongoDistributedSemaphore.java`)
A capacity-`N` semaphore is `N` slot locks (`name#0…name#N-1`); a permit = winning any free
slot, probed in randomized order. Reuses the lock entirely, so crash-recovery via lease
expiry comes for free.

### Kubernetes, 4 data centers
Kustomize `base/` + overlays `dc1…dc4`, each with its own namespace, region `nodeSelector`,
`DATA_CENTER` env, actuator liveness/readiness probes, non-root read-only-rootfs pods. The
README explains all four DCs must point at the **same** MongoDB for cross-DC exclusivity.

### Logging
SLF4J everywhere, parameterized, entry/exit at DEBUG, failures at ERROR, no secrets.

### Javadoc
On every class and method (`./gradlew javadoc` passes with doclint).

### README.md
Covers design, config, build, API, Docker, and deployment.

## Verification
- ✅ `compileJava` / `compileTestJava` — pass
- ✅ `bootJar` — produces `build/libs/app.jar`
- ✅ `javadoc` — passes with `-Xdoclint:all`
- ✅ All 13 k8s YAML files parse
- ✅ Verified Boot 4.1.0 artifacts in Maven Central; fixed Testcontainers coords (Boot's BOM
  pulls Testcontainers **2.0.5**, whose modules renamed to `testcontainers-junit-jupiter` /
  `testcontainers-mongodb`)

## Not run here (no infra on this machine)
- **Integration tests** (`./gradlew test`) need **Docker** for Testcontainers — they compile
  but weren't executed (no Docker installed).
- **`kubectl kustomize`** validation — no `kubectl` installed; YAML was lint-checked instead.

To exercise them:
```bash
docker run -d -p 27017:27017 mongo:8.0
./gradlew test
kubectl kustomize k8s/overlays/dc1
```

## Design note
A `LeaderHeartbeatJob` and a `LockController` were added purely to *demonstrate* the lock
across pods. If you only want the lock/semaphore library, those two `demo`/`web` classes can
be deleted without touching the core.

## Project file tree
```
.dockerignore
.gitignore
Dockerfile
README.md
build.gradle
settings.gradle
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
docs/request_prompt.md
k8s/base/configmap.yaml
k8s/base/deployment.yaml
k8s/base/kustomization.yaml
k8s/base/secret.example.yaml
k8s/base/service.yaml
k8s/overlays/dc1/deployment-patch.yaml
k8s/overlays/dc1/kustomization.yaml
k8s/overlays/dc2/deployment-patch.yaml
k8s/overlays/dc2/kustomization.yaml
k8s/overlays/dc3/deployment-patch.yaml
k8s/overlays/dc3/kustomization.yaml
k8s/overlays/dc4/deployment-patch.yaml
k8s/overlays/dc4/kustomization.yaml
src/main/java/com/example/dlock/DistributedSemaphoreApplication.java
src/main/java/com/example/dlock/config/DistributedLockProperties.java
src/main/java/com/example/dlock/config/InstanceIdentity.java
src/main/java/com/example/dlock/config/LockIndexInitializer.java
src/main/java/com/example/dlock/demo/LeaderHeartbeatJob.java
src/main/java/com/example/dlock/lock/DistributedLock.java
src/main/java/com/example/dlock/lock/LockAcquisitionException.java
src/main/java/com/example/dlock/lock/LockDocument.java
src/main/java/com/example/dlock/lock/LockInterruptedException.java
src/main/java/com/example/dlock/lock/MongoDistributedLock.java
src/main/java/com/example/dlock/semaphore/DistributedSemaphore.java
src/main/java/com/example/dlock/semaphore/MongoDistributedSemaphore.java
src/main/java/com/example/dlock/semaphore/SemaphorePermit.java
src/main/java/com/example/dlock/web/LockController.java
src/main/resources/application.yaml
src/test/java/com/example/dlock/lock/MongoDistributedLockIntegrationTest.java
src/test/java/com/example/dlock/semaphore/MongoDistributedSemaphoreIntegrationTest.java
```
