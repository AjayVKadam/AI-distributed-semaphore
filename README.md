# Distributed Semaphore

A Spring Boot service that provides a **distributed lock** and a **distributed counting
semaphore** backed by MongoDB. It is designed to run as many identical replicas
(Kubernetes pods) spread across **four data centers**, all coordinating through a single
shared MongoDB deployment using atomic operations — no replica needs to know about any
other.

- **Spring Boot 4.1** on **Java 25**, built with the **Gradle wrapper** (Groovy DSL)
- **Virtual threads** (Project Loom) for request handling
- Coordination via **atomic MongoDB `findAndModify` + upsert** — GA features only
- Self-healing leases: a crashed holder's lock is automatically reclaimed on expiry
- Kustomize manifests for **4 data centers**, with health probes and least-privilege pods

---

## How it works

### Distributed lock

Each lock is a single document in the `distributed_locks` collection, keyed by the lock
name (`_id`). Holding the lock means a document exists whose `expiresAt` is in the future.

Acquisition is a **single atomic `findAndModify` with `upsert = true`** whose query only
matches a *free* (expired) lock:

```
query  : { _id: name, expiresAt: { $lt: now } }
update : { $set: { owner, acquiredAt, expiresAt: now + 15m } }
upsert : true
```

| State on the server                | What happens                                              | Result    |
|------------------------------------|-----------------------------------------------------------|-----------|
| No document exists                 | Query matches nothing → upsert **inserts** a new document | Acquired  |
| Expired document exists            | Query matches it → update re-stamps the owner             | Acquired  |
| Live (non-expired) document exists | Query matches nothing → upsert tries a second insert with the same `_id` → **duplicate-key error** | Not acquired |

Because the unique `_id` index serializes the insert, **no two instances can ever hold
the same lock at the same instant**, even across data centers. The lease is **15 minutes**
by default.

If a lock cannot be acquired, the blocking `acquire(...)` retries **every 15 seconds for
up to 3 minutes** before giving up. All three values are configurable.

A TTL index on `expiresAt` lets MongoDB reclaim expired documents in the background;
correctness does not depend on it, since the acquisition query already treats an expired
document as free.

### Distributed semaphore

The semaphore is a direct extension of the lock. A semaphore named `downloads` with
capacity `N` is modelled as `N` independent slot locks: `downloads#0`, `downloads#1`, …,
`downloads#(N-1)`. **Acquiring a permit means winning any one free slot lock**; releasing
returns it. Slots are probed in randomized order to spread contending instances.

Because each slot is a fully independent atomic lock, the number of concurrently held
permits can never exceed the capacity, and a permit held by a crashed instance is freed
when its lease expires.

---

## Project layout

```
src/main/java/com/example/dlock/
  DistributedSemaphoreApplication.java   # entry point
  config/
    DistributedLockProperties.java       # distributed-lock.* config (lease/timeout/retry)
    InstanceIdentity.java                # per-pod owner id (POD_NAME / HOSTNAME + UUID)
    LockIndexInitializer.java            # TTL index on expiresAt
  lock/
    DistributedLock.java                 # lock API
    MongoDistributedLock.java            # atomic findAndModify/upsert implementation
    LockDocument.java                    # MongoDB document
    LockAcquisitionException.java
    LockInterruptedException.java
  semaphore/
    DistributedSemaphore.java            # semaphore API
    MongoDistributedSemaphore.java       # N-slot implementation over the lock
    SemaphorePermit.java                 # opaque permit token
  web/
    LockController.java                  # REST endpoints for demo / integration tests
  demo/
    LeaderHeartbeatJob.java              # scheduled single-leader demonstration
k8s/
  base/                                  # Deployment, Service, ConfigMap, Secret example
  overlays/dc1..dc4/                     # one Kustomize overlay per data center
Dockerfile                               # multi-stage build (JDK 25 build, JRE 25 runtime)
```

---

## Configuration

`src/main/resources/application.yaml` (override via environment variables):

| Property                          | Env var                             | Default | Meaning                                    |
|-----------------------------------|-------------------------------------|---------|--------------------------------------------|
| `spring.data.mongodb.uri`         | `MONGODB_URI`                       | local   | MongoDB connection string                  |
| `distributed-lock.lease-duration` | `DISTRIBUTED_LOCK_LEASE_DURATION`   | `15m`   | How long an acquisition stays valid        |
| `distributed-lock.acquire-timeout`| `DISTRIBUTED_LOCK_ACQUIRE_TIMEOUT`  | `3m`    | Max time a blocking acquire keeps retrying |
| `distributed-lock.retry-interval` | `DISTRIBUTED_LOCK_RETRY_INTERVAL`   | `15s`   | Delay between retries while blocking       |

---

## Building and running

### Prerequisites
- JDK 25 (the Gradle toolchain will also resolve one if configured)
- Docker (for the Testcontainers-based integration tests and for building the image)
- A reachable MongoDB for running locally

### Build & test
```bash
./gradlew build           # compile, run tests, assemble the boot jar
./gradlew javadoc         # generate API docs under build/docs/javadoc
```

### Run locally
```bash
# start a throwaway MongoDB
docker run -d --name mongo -p 27017:27017 mongo:8.0

MONGODB_URI=mongodb://localhost:27017/distributed_locks ./gradlew bootRun
```

### Try the API
Every response includes the `instanceId` of the pod that handled it.

```bash
# Lock
curl -X POST   localhost:8080/api/locks/orders/try      # 200 acquired / 409 held
curl    -s     localhost:8080/api/locks/orders/held
curl -X DELETE localhost:8080/api/locks/orders          # release

# Semaphore with capacity 3
curl -X POST 'localhost:8080/api/semaphores/downloads/try?permits=3'   # -> {"slot":N,...}
curl -X DELETE localhost:8080/api/semaphores/downloads/permits/0       # release slot 0
```

---

## Container image

```bash
docker build -t distributed-semaphore:latest .
```

The image builds with JDK 25, runs on a JRE 25 base as a non-root user with a read-only
root filesystem.

---

## Deploying to Kubernetes (4 data centers)

The manifests use Kustomize: a shared `base/` plus one overlay per data center under
`k8s/overlays/dc1` … `dc4`. Each overlay deploys into its own namespace
(`distributed-semaphore-dcN`), pins pods to that region's nodes
(`topology.kubernetes.io/region`), and sets a `DATA_CENTER` env var.

> **Important:** For locks to be mutually exclusive *across* data centers, every data
> center must point at the **same logical MongoDB** (a global replica set or sharded
> cluster reachable from each DC). If each DC used its own isolated MongoDB, locks would
> only be exclusive within a single DC.

### 1. Provide the MongoDB Secret in each cluster
`secret.example.yaml` is a template only — never commit real credentials.
```bash
kubectl create namespace distributed-semaphore-dc1
kubectl -n distributed-semaphore-dc1 create secret generic mongodb-credentials \
  --from-literal=uri='mongodb://app_user:***@mongo-0.global,mongo-1.global/distributed_locks?replicaSet=rs0&authSource=admin&w=majority'
```

### 2. Apply the overlay for each data center
```bash
kubectl apply -k k8s/overlays/dc1
kubectl apply -k k8s/overlays/dc2
kubectl apply -k k8s/overlays/dc3
kubectl apply -k k8s/overlays/dc4
```

Preview the rendered manifests without applying:
```bash
kubectl kustomize k8s/overlays/dc1
```

### Health probes
The container exposes Spring Boot Actuator probes consumed by Kubernetes:
- Liveness: `/actuator/health/liveness`
- Readiness / startup: `/actuator/health/readiness`

### Observing single-leader behavior
`LeaderHeartbeatJob` runs in every pod every 30s and competes for the `leader-election`
lock. Tail logs across pods (and across data centers) and you will see exactly one pod
log `I am the leader` / `Became the leader` at a time, while the rest log `standing by`.

---

## Logging

Uses SLF4J with parameterized messages. Every non-trivial method logs entry and exit at
`DEBUG` (`com.example.dlock` is set to `DEBUG` by default); failures are logged at `ERROR`
with context. No secrets are logged.

---

## Testing

Integration tests run against a real MongoDB via Testcontainers (Docker required):
- `MongoDistributedLockIntegrationTest` — mutual exclusion, release, non-owner release,
  lease expiry / takeover, renewal.
- `MongoDistributedSemaphoreIntegrationTest` — capacity enforcement, permit release,
  invalid capacity.

```bash
./gradlew test
```
