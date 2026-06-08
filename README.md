# dicraft-framework-spi-lock

[![license](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![java](https://img.shields.io/badge/java-8+-orange.svg)](https://www.oracle.com/java/)
[![spring](https://img.shields.io/badge/spring-5.x-green.svg)](https://spring.io/)
[![redisson](https://img.shields.io/badge/redisson-3.x-red.svg)](https://redisson.org/)

[中文文档](README_zh.md) ｜ English

distributedLock Tool
A declarative distributed lock component based on Redisson, providing method-level distributed locking via annotation + Spring AOP.

## Highlights

- **Zero Intrusion** — Just add `@DistributeLock` annotation, no boilerplate lock code needed
- **SpEL Support** — Dynamic lock key resolution via Spring Expression Language
- **Flexible Configuration** — Annotation-level and global-level config with clear priority chain
- **Auto Renewal** — Redisson Watchdog enabled by default, no worry about lock expiration
- **Observable** — Full lifecycle logging for lock acquisition, hold time and release

## Quick Start

### Add Dependency

```xml
<dependency>
    <groupId>cn.dicraft</groupId>
    <artifactId>dicraft-framework-spi-lock</artifactId>
    <version>1.0.1</version>
</dependency>
```

### Usage

```java
@DistributeLock(scene = "order", key = "#orderId")
public void createOrder(String orderId) {
    // business logic
}
```

<details open>
<summary>More examples</summary>

```java
// Scene-only lock (no key), locks on the entire scene
@DistributeLock(scene = "inventory-sync")
public void syncInventory() { ... }

// Custom lease time and wait time
@DistributeLock(scene = "payment", key = "#paymentId", leaseTime = 10000, waitTime = 5000)
public void processPayment(String paymentId) { ... }

// Multi-key lock: keys are sorted and joined with "."
// e.g. keys resolved to "acc-A" and "acc-B" → lock key "transfer#acc-A.acc-B"
@DistributeLock(scene = "transfer", keys = {"#fromAccountId", "#toAccountId"})
public void transfer(String fromAccountId, String toAccountId, BigDecimal amount) { ... }

// Collection/Array as key: elements are joined with "."
// e.g. ids = [3, 1, 2] → lock key "batch-delete#1.2.3"  (elements sorted)
@DistributeLock(scene = "batch-delete", keys = {"#ids"})
public void batchDelete(List<Long> ids) { ... }
```

</details>

### Annotation Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `scene` | String | required | Business scene identifier for the lock |
| `key` | String | `""` | Lock key parameter, supports SpEL expression |
| `keys` | String[] | `{}` | Multiple lock key parameters (SpEL), sorted and joined with `.`. Takes precedence over `key` |
| `leaseTime` | long | unset | Lock lease time (ms), falls back to global config or default |
| `waitTime` | long | unset | Lock wait time (ms), falls back to global config or default |

### Global Configuration

Configure global lease time, wait time and key prefix in `application.yml` or `application.properties`:

```yaml
dicraft:
  lock:
    lease-time: 30000  # global lock lease time (ms)
    wait-time: 5000    # global lock wait time (ms)
    key-prefix: my-app # global lock key prefix (optional)
```

**Configuration Priority**: Annotation > Global Config > Default (`-1`)

| Source | Description |
|--------|-------------|
| Annotation | `@DistributeLock(leaseTime = 10000)` — highest priority |
| Global Config | `dicraft.lock.lease-time` / `dicraft.lock.wait-time` — used when annotation value is not set |
| Default | `-1` — enables Watchdog auto-renewal / waits indefinitely |

### Lock Key Generation

The final lock key format is `scene#key`, where `key` is resolved via SpEL. If `key` is empty, only `scene` is used as the lock key.

When `dicraft.lock.key-prefix` is configured, the prefix is prepended with a colon separator:

| key-prefix | scene | key | Final Lock Key |
|------------|-------|-----|----------------|
| *(not set)* | `order` | `#orderId` → `123` | `order#123` |
| *(not set)* | `order` | *(empty)* | `order` |
| `my-app` | `order` | `#orderId` → `123` | `my-app:order#123` |
| `my-app` | `order` | *(empty)* | `my-app:order` |

This is useful when multiple microservices share the same Redis instance — each service can use its own prefix to avoid lock key collisions.

#### Multi-Key (`keys`) Resolution

When `keys` is used instead of `key`, each SpEL expression is evaluated independently, and the resolved values are **sorted lexicographically** then joined with `.` to form the key segment:

```java
@DistributeLock(scene = "transfer", keys = {"#fromAccountId", "#toAccountId"})
public void transfer(String fromAccountId, String toAccountId, BigDecimal amount) { ... }
```

| fromAccountId | toAccountId | Final Lock Key |
|---------------|-------------|----------------|
| `"acc-A"` | `"acc-B"` | `transfer#acc-A.acc-B` |
| `"acc-B"` | `"acc-A"` | `transfer#acc-A.acc-B` |

The lexicographic sorting ensures that the same set of resources always produces the same lock key, regardless of parameter order — helping prevent ordering-related deadlocks.

When a key expression evaluates to a **Collection or Array**, its elements are automatically joined with `.` as a single key segment:

```java
@DistributeLock(scene = "batch-delete", keys = {"#ids"})
public void batchDelete(List<Long> ids) { ... }
```

| ids | Final Lock Key |
|-----|----------------|
| `[3, 1, 2]` | `batch-delete#1.2.3` |
| `[1, 2, 3]` | `batch-delete#1.2.3` |

Since all key segments are sorted, the element order of the input collection does not matter — `[3, 1, 2]` and `[1, 2, 3]` produce the same lock key.

### Locking Strategy

| waitTime | leaseTime | Behavior |
|----------|-----------|----------|
| `-1` (default) | `-1` (default) | `lock()` — wait indefinitely + Watchdog auto-renewal |
| `-1` (default) | custom | `lock(leaseTime, ms)` — wait indefinitely + fixed lease |
| custom | `-1` (default) | `tryLock(waitTime, ms)` — timed wait + Watchdog renewal |
| custom | custom | `tryLock(waitTime, leaseTime, ms)` — timed wait + fixed lease |

## Deadlock Considerations

Each `@DistributeLock` invocation holds exactly one lock and releases it in a `finally` block, so lock leaks within a single call are not possible.

However, if a call chain nests multiple `@DistributeLock` annotations with **different keys** (e.g. thread A locks `order#1` then `order#2`, while thread B locks `order#2` then `order#1`), a circular-wait deadlock can occur.

**Recommendations:**

- Prefer acquiring a single lock per business operation whenever possible.
- If multiple locks are unavoidable, establish a project-wide convention for consistent lock ordering (e.g. lexicographic order of lock keys).
- When using `keys`, the resolved segments are automatically sorted to produce a deterministic lock key, reducing ordering-related deadlock risk.

## Prerequisites

- Java 8+
- Spring Context 5.x / 6.X
- Spring Boot 2.x / 3.x (auto-configuration compatible with both)
- Redisson 3.x (requires a `RedissonClient` bean configured by the user)

## License

Copyright 2025 dicraft.

Distributed under the terms of the [Apache License 2.0](LICENSE).
