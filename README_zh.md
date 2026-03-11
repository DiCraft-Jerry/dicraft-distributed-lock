# dicraft-framework-spi-lock

[![license](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![java](https://img.shields.io/badge/java-8+-orange.svg)](https://www.oracle.com/java/)
[![spring](https://img.shields.io/badge/spring-5.x-green.svg)](https://spring.io/)
[![redisson](https://img.shields.io/badge/redisson-3.x-red.svg)](https://redisson.org/)

[English](README.md) ｜ 中文文档

分布式锁工具
基于 Redisson 的声明式分布式锁组件，通过注解 + Spring AOP 实现方法级别的分布式锁能力。

## 亮点

- **零侵入** — 只需添加 `@DistributeLock` 注解，无需编写样板式加锁代码
- **SpEL 支持** — 通过 Spring 表达式语言动态解析锁 Key
- **灵活配置** — 支持注解级别与全局级别配置，优先级链路清晰
- **自动续期** — 默认启用 Redisson Watchdog，无需担心锁过期
- **可观测** — 加锁、持有、释放全链路日志追踪

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>cn.dicraft</groupId>
    <artifactId>dicraft-framework-spi-lock</artifactId>
    <version>1.0.1</version>
</dependency>
```

### 使用注解

```java
@DistributeLock(scene = "order", key = "#orderId")
public void createOrder(String orderId) {
    // 业务逻辑
}
```

<details>
<summary>更多示例</summary>

```java
// 仅使用 scene（不指定 key），锁定整个场景
@DistributeLock(scene = "inventory-sync")
public void syncInventory() { ... }

// 自定义租期和等待时长
@DistributeLock(scene = "payment", key = "#paymentId", leaseTime = 10000, waitTime = 5000)
public void processPayment(String paymentId) { ... }
```

</details>

### 注解参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `scene` | String | 必填 | 锁的业务场景标识 |
| `key` | String | `""` | 锁参数，支持 SpEL 表达式 |
| `leaseTime` | long | 未设置 | 锁租期（毫秒），未设置时使用全局配置或默认值 |
| `waitTime` | long | 未设置 | 加锁等待时长（毫秒），未设置时使用全局配置或默认值 |

### 全局配置

在 `application.yml` 或 `application.properties` 中配置全局的租期、等待时长和 Key 前缀：

```yaml
dicraft:
  lock:
    lease-time: 30000  # 全局锁租期（毫秒）
    wait-time: 5000    # 全局加锁等待时长（毫秒）
    key-prefix: my-app # 全局锁 Key 前缀（可选）
```

**配置优先级**: 注解指定 > 全局配置 > 默认值（`-1`）

| 来源 | 说明 |
|------|------|
| 注解指定 | `@DistributeLock(leaseTime = 10000)` 直接在注解上指定，优先级最高 |
| 全局配置 | `dicraft.lock.lease-time` / `dicraft.lock.wait-time`，注解未指定时生效 |
| 默认值 | `-1`，启用 Watchdog 自动续期 / 无限等待 |

### 锁 Key 生成规则

最终锁 Key 格式为 `scene#key`，其中 key 通过 SpEL 解析得到。若 key 为空，则直接使用 scene 作为锁 Key。

配置 `dicraft.lock.key-prefix` 后，前缀会以冒号分隔拼接在最前面：

| key-prefix | scene | key | 最终锁 Key |
|------------|-------|-----|-----------|
| *（未设置）* | `order` | `#orderId` → `123` | `order#123` |
| *（未设置）* | `order` | *（空）* | `order` |
| `my-app` | `order` | `#orderId` → `123` | `my-app:order#123` |
| `my-app` | `order` | *（空）* | `my-app:order` |

当多个微服务共享同一个 Redis 实例时，通过配置不同的前缀可以避免锁 Key 冲突。

### 加锁策略

| waitTime | leaseTime | 行为 |
|----------|-----------|------|
| `-1`（默认） | `-1`（默认） | `lock()` — 无限等待 + Watchdog 自动续期 |
| `-1`（默认） | 自定义 | `lock(leaseTime, ms)` — 无限等待 + 固定租期 |
| 自定义 | `-1`（默认） | `tryLock(waitTime, ms)` — 限时等待 + Watchdog 续期 |
| 自定义 | 自定义 | `tryLock(waitTime, leaseTime, ms)` — 限时等待 + 固定租期 |

## 前置依赖

- Java 8+
- Spring Context 5.x / 6.X
- Spring Boot 2.x / 3.x（自动配置兼容两个版本）
- Redisson 3.x（需自行配置 `RedissonClient` Bean）

## 许可证

Copyright 2025 dicraft.

本项目基于 [Apache License 2.0](LICENSE) 许可证开源。
