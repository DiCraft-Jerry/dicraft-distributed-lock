# CLAUDE_zh.md

> For the English version, see [CLAUDE.md](CLAUDE.md)

## 项目概述

**dicraft-distributed-lock** 是一个轻量级、零侵入的 Spring Boot Starter，通过单个 `@DistributeLock` 注解提供基于 Redisson 的声明式分布式锁。项目基于 Java 8+，支持 Spring Boot 2.x/3.x，以 `cn.dicraft:dicraft-framework-spi-lock` 发布到 Maven Central。

## 构建与测试命令

```bash
mvn clean test                    # 运行全部测试
mvn clean package -DskipTests     # 构建 JAR（跳过测试）
mvn clean package                 # 构建 JAR（含测试）
```

## 技术栈

| 层次 | 技术 | 备注 |
|------|------|------|
| 语言 | Java 8 | 编译器插件 source/target 为 8 |
| 构建 | Maven 3.6+ | 无 wrapper（`.mvn/` 为空） |
| DI/AOP | Spring Context 5.3.39 + AspectJ 1.9.22.1 | `provided` 作用域 |
| 自动配置 | Spring Boot Autoconfigure 2.7.18 | 通过 `AutoConfiguration.imports` 兼容 Boot 3.x |
| 锁引擎 | Redisson 3.38.1 | `provided` 作用域 |
| 工具库 | Apache Commons Lang3 3.18.0 | 唯一非 provided 的编译依赖 |
| 样板代码 | Lombok 1.18.28 | `provided` 作用域 |
| 测试 | JUnit 5.10 + Mockito 4.11 + AssertJ 3.24 | |

## 架构

单模块 Maven 项目，按职责分包：

```
cn.dicraft
  ├── DistributeLockConfigConstant    # 哨兵值、默认值（UNSET、-1、""）
  ├── annotation/
  │   └── DistributeLock              # @interface：scene、key、keys、leaseTime、waitTime
  ├── aspect/
  │   └── DistributeLockAspect        # @Around 环绕通知：加锁、Key 解析、释放
  ├── config/
  │   ├── DistributeLockAutoConfiguration   # 条件 Bean 注册
  │   └── DistributeLockProperties         # @ConfigurationProperties 持有者
  └── exception/
      └── DistributeLockException     # 继承 RuntimeException
```

自动配置注册文件分两套：
- `META-INF/spring.factories`（Spring Boot 2.x）
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（Spring Boot 3.x）

## 编码规范

### 命名
- 类名：`PascalCase`（如 `DistributeLockAspect`）
- 方法/变量：`camelCase`（如 `analyseKeyExpression`、`resolveKeyPrefix`）
- 常量：`UPPER_SNAKE_CASE`（如 `UNSET`、`DEFAULT_LEASE_TIME`）
- 包名：`cn.dicraft.<职责>`（中国域名倒序约定）

### 格式
- 4 空格缩进，K&R 大括号风格（左大括号在同一行）
- 禁止通配符 import——所有类显式导入
- 先 public 后 private，测试文件中用注释分隔各区块（如 `// ===== 区块名 =====`）

### 文档
- **每个 public 类、public 方法、注解属性都必须有 Javadoc**，使用英文编写
- Javadoc 使用 `{@code}`、`{@link}`、`<p>`、`<ul>/<li>`、`<pre>` 标签
- 作者标签：`@author 烛远`
- 所有日志和注释使用英文
- 日志统一使用 `[DistributeLock]` 前缀，便于 grep 检索

### 编码模式
- **Lombok**：所有类使用 `@Slf4j`、`@RequiredArgsConstructor`、`@Getter`
- **构造器注入**：字段为 `private final`，通过 Lombok 生成的构造器注入
- **Guard Clause**：遇到 null/空值提前返回
- **防御性日志**：对可能静默失败的场景记录 warning（如缺少 `-parameters` 编译选项导致 SpEL 参数名无法解析）
- **快速失败**：加锁失败立即抛 `DistributeLockException`
- **try/finally**：锁释放始终在 `finally` 块中
- **@Order(Integer.MIN_VALUE)**：切面以最高优先级执行
- **条件 Bean**：使用 `@ConditionalOnBean`、`@ConditionalOnMissingBean`、`@ConditionalOnClass`
- 除 `commons-lang3` 外所有依赖均为 `provided` 作用域（这是 Starter 库，不是 Fat JAR）
- 常量类使用 private 构造器防止实例化
- 线程安全的单例对象缓存为 `static final` 字段（如 `SpelExpressionParser`、`DefaultParameterNameDiscoverer`）

### 三级配置优先级（始终遵循）
注解值 > 全局配置（`dicraft.lock.*`）> 硬编码默认值（`-1`）

## 测试规范

- 测试类名 = 被测类名 + `Test` 后缀
- 单元测试使用 `@ExtendWith(MockitoExtension.class)`
- 自动配置集成测试使用 `ApplicationContextRunner`
- 依赖使用 `@Mock`，方法名描述测试场景（如 `withRedissonClientBean_allBeansRegistered`）
- 断言使用 AssertJ（`assertThat(...).isEqualTo(...)`）——不使用 JUnit 断言
- 仅为反射而存在的测试桩方法使用 `@SuppressWarnings("unused")`
- 内部类 `StubMethods` 存放带注解的方法供切面拦截
- 测试区块用 `// ===== 区块名 =====` 注释分隔
- 辅助方法：`mockMethod()`、`mockLockHeld()`、`createAspect()`

## Git 规范

- **分支策略**：`master`（稳定版）、`develop`（主开发分支）、`feature/*`、`fix/*`——均从 `develop` 分支
- **提交格式**：Conventional Commits——`<type>: <简短描述>`
  - 类型：`feat`、`fix`、`docs`、`refactor`、`test`、`chore`
  - 强制祈使语气，小写，描述性强
  - 重大变更使用多行正文，以 `-` 列表项描述
- **PR 目标**：始终提交到 `develop`，绝不直接提交到 `master`
- **更新日志**：在 `CHANGELOG.md` 中遵循 Keep a Changelog 格式

## CI/CD

GitHub Actions 工作流（`release.yml`）在 `release.published` 时触发：
- Checkout → Setup JDK 8 (Temurin) → `mvn clean package -DskipTests` → Upload JAR 到 Release
- `release` Maven profile 添加 GPG 签名 + Sonatype Central 发布

## 范围约束

- 这是**库**而非应用——所有运行时依赖为 `provided`
- 工具类/常量类使用 private 构造器
- 无充分理由不得新增依赖
- 向后兼容很重要——Spring Boot 2.x 支持与 3.x 并存
