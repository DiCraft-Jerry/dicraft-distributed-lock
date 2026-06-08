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

<details open>
<summary>更多示例</summary>

```java
// 仅使用 scene（不指定 key），锁定整个场景
@DistributeLock(scene = "inventory-sync")
public void syncInventory() { ... }

// 自定义租期和等待时长
@DistributeLock(scene = "payment", key = "#paymentId", leaseTime = 10000, waitTime = 5000)
public void processPayment(String paymentId) { ... }

// 多 key 锁：key 按字典序排列后用 "." 拼接
// 如 keys 解析为 "acc-A" 和 "acc-B" → 锁 key 为 "transfer#acc-A.acc-B"
@DistributeLock(scene = "transfer", keys = {"#fromAccountId", "#toAccountId"})
public void transfer(String fromAccountId, String toAccountId, BigDecimal amount) { ... }

// 列表/数组作为 key：元素用 "." 拼接
// 如 ids = [3, 1, 2] → 锁 key 为 "batch-delete#1.2.3"（元素排序后拼接）
@DistributeLock(scene = "batch-delete", keys = {"#ids"})
public void batchDelete(List<Long> ids) { ... }
```

</details>

### 注解参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `scene` | String | 必填 | 锁的业务场景标识 |
| `key` | String | `""` | 锁参数，支持 SpEL 表达式 |
| `keys` | String[] | `{}` | 多个锁参数（SpEL），求值后按字典序排列并用 `.` 拼接，优先级高于 `key` |
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

#### 多 Key（`keys`）解析规则

使用 `keys` 代替 `key` 时，每个 SpEL 表达式独立求值，结果按**字典序排列**后用 `.` 拼接：

```java
@DistributeLock(scene = "transfer", keys = {"#fromAccountId", "#toAccountId"})
public void transfer(String fromAccountId, String toAccountId, BigDecimal amount) { ... }
```

| fromAccountId | toAccountId | 最终锁 Key |
|---------------|-------------|-----------|
| `"acc-A"` | `"acc-B"` | `transfer#acc-A.acc-B` |
| `"acc-B"` | `"acc-A"` | `transfer#acc-A.acc-B` |

字典序排列确保相同的资源集合无论参数顺序如何，始终生成相同的锁 Key，有助于防止因加锁顺序不一致导致的死锁。

当 key 表达式求值结果为**列表（Collection）或数组（Array）**时，其元素会自动用 `.` 拼接为一个 key 段：

```java
@DistributeLock(scene = "batch-delete", keys = {"#ids"})
public void batchDelete(List<Long> ids) { ... }
```

| ids | 最终锁 Key |
|-----|-----------|
| `[3, 1, 2]` | `batch-delete#1.2.3` |
| `[1, 2, 3]` | `batch-delete#1.2.3` |

由于所有 key 段都经过排序，输入集合的元素顺序不影响结果 — `[3, 1, 2]` 和 `[1, 2, 3]` 会生成相同的锁 Key。

### 加锁策略

| waitTime | leaseTime | 行为 |
|----------|-----------|------|
| `-1`（默认） | `-1`（默认） | `lock()` — 无限等待 + Watchdog 自动续期 |
| `-1`（默认） | 自定义 | `lock(leaseTime, ms)` — 无限等待 + 固定租期 |
| 自定义 | `-1`（默认） | `tryLock(waitTime, ms)` — 限时等待 + Watchdog 续期 |
| 自定义 | 自定义 | `tryLock(waitTime, leaseTime, ms)` — 限时等待 + 固定租期 |

## 死锁注意事项

每次 `@DistributeLock` 调用仅持有一把锁，且在 `finally` 块中释放，因此单次调用不会发生锁泄漏。

但如果调用链中嵌套了多个 `@DistributeLock` 且锁的 key **不同**（如线程 A 先锁 `order#1` 再锁 `order#2`，线程 B 先锁 `order#2` 再锁 `order#1`），会形成环路等待导致死锁。

**建议：**

- 同一业务操作尽量只加一把锁
- 若必须加多把锁，全项目约定统一的加锁顺序（如按 lockKey 字典序）
- 使用 `keys` 时，解析后的 key 段会自动排序以生成确定性的锁 Key，降低因顺序不一致导致的死锁风险

## 前置依赖

- Java 8+
- Spring Context 5.x / 6.X
- Spring Boot 2.x / 3.x（自动配置兼容两个版本）
- Redisson 3.x（需自行配置 `RedissonClient` Bean）

## 许可证

Copyright 2025 dicraft.

本项目基于 [Apache License 2.0](LICENSE) 许可证开源。
