# Save this file as `CLAUDE.md` in the ~/.claude directory to set global code generation standards for Java projects.

# Java Code Generation Standards

Global conventions for generating Java code. These apply to every project
unless a project-level `CLAUDE.md` or explicit instruction overrides them.

## Build

- Use **Gradle with the Groovy DSL** — `build.gradle`, `settings.gradle`.
  Do **not** use the Kotlin DSL (`*.gradle.kts`).
- Always use the Gradle wrapper (`./gradlew`).
- Manage dependency versions through the Spring dependency-management plugin /
  Boot BOM rather than pinning individual Spring module versions by hand.

## Language & Runtime

- Target **Java 25** for `sourceCompatibility`, `targetCompatibility`, and the
  Gradle toolchain (`java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }`).
- **Do not use preview features.** Never pass `--enable-preview`, and avoid any
  language feature or API still marked *preview* or *incubator* in Java 25.
  Use only stable / GA features.

## Frameworks

- Use the **latest GA releases** of Spring Framework and Spring Boot.
  Do **not** use milestone (`-M`), release-candidate (`-RC`), or snapshot builds.
- When the current GA version is unknown, check `start.spring.io` or
  `spring.io/projects` and resolve transitive versions via the Boot BOM.
- Reference point (current as of June 2026 — verify before use):
  Spring Boot **4.1.x**, Spring Framework **7.0.x+**. Spring Boot 4.x supports
  Java 17–26, so Java 25 is compatible.

## Configuration

- Use **`application.yaml`** for Spring configuration — never `application.properties`.
  This applies to profile-specific files too (e.g. `application-dev.yaml`).
- Keep YAML keys hierarchical and avoid duplicating prefixes.

## Concurrency

- Use **virtual threads** (Project Loom) for request handling and blocking I/O.
- In Spring Boot, enable them with `spring.threads.virtual.enabled=true`.
- For manual executors, use `Executors.newVirtualThreadPerTaskExecutor()`.
- Create one virtual thread per task — never pool them.
  (On Java 25, JEP 491 removes `synchronized` pinning, so locking choice is no
  longer a correctness issue, but keep tasks short and non-blocking on shared state.)

## Logging

- Use **SLF4J** — `private static final Logger log = LoggerFactory.getLogger(Xxx.class);`.
- Log **method entry and exit** for all non-trivial methods:
  - **Entry:** method name + parameters at `DEBUG`.
  - **Exit:** method name + return value (or elapsed time) at `DEBUG`.
  - On failure, log the exception at `ERROR` (with context, without swallowing it).
- Always use **parameterized logging** (`log.debug("entry: process({})", id)`),
  never string concatenation.
- Never log secrets, credentials, tokens, or PII — mask or redact such values.
