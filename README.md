# dicraft-framework-spi-lock

[![license](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![java](https://img.shields.io/badge/java-8+-orange.svg)](https://www.oracle.com/java/)
[![spring](https://img.shields.io/badge/spring-5.x-green.svg)](https://spring.io/)
[![redisson](https://img.shields.io/badge/redisson-3.x-red.svg)](https://redisson.org/)

[中文文档](README_zh.md) ｜ English

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
    <version>1.0</version>
</dependency>
```

### Usage

```java
@DistributeLock(scene = "order", key = "#orderId")
public void createOrder(String orderId) {
    // business logic
}
```

<details>
<summary>More examples</summary>

```java
// Scene-only lock (no key), locks on the entire scene
@DistributeLock(scene = "inventory-sync")
public void syncInventory() { ... }

// Custom lease time and wait time
@DistributeLock(scene = "payment", key = "#paymentId", leaseTime = 10000, waitTime = 5000)
public void processPayment(String paymentId) { ... }
```

</details>

### Annotation Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `scene` | String | required | Business scene identifier for the lock |
| `key` | String | `""` | Lock key parameter, supports SpEL expression |
| `leaseTime` | long | unset | Lock lease time (ms), falls back to global config or default |
| `waitTime` | long | unset | Lock wait time (ms), falls back to global config or default |

### Global Configuration

Configure global lease time and wait time in `application.yml` or `application.properties`:

```yaml
dicraft:
  lock:
    lease-time: 30000  # global lock lease time (ms)
    wait-time: 5000    # global lock wait time (ms)
```

**Configuration Priority**: Annotation > Global Config > Default (`-1`)

| Source | Description |
|--------|-------------|
| Annotation | `@DistributeLock(leaseTime = 10000)` — highest priority |
| Global Config | `dicraft.lock.lease-time` / `dicraft.lock.wait-time` — used when annotation value is not set |
| Default | `-1` — enables Watchdog auto-renewal / waits indefinitely |

### Lock Key Generation

The final lock key format is `scene#key`, where `key` is resolved via SpEL. If `key` is empty, only `scene` is used as the lock key.

### Locking Strategy

| waitTime | leaseTime | Behavior |
|----------|-----------|----------|
| `-1` (default) | `-1` (default) | `lock()` — wait indefinitely + Watchdog auto-renewal |
| `-1` (default) | custom | `lock(leaseTime, ms)` — wait indefinitely + fixed lease |
| custom | `-1` (default) | `tryLock(waitTime, ms)` — timed wait + Watchdog renewal |
| custom | custom | `tryLock(waitTime, leaseTime, ms)` — timed wait + fixed lease |

## Prerequisites

- Java 8+
- Spring Context 5.x
- Redisson 3.x (requires a `RedissonClient` bean configured by the user)

## License

Copyright 2025 dicraft.

Distributed under the terms of the [Apache License 2.0](LICENSE).
