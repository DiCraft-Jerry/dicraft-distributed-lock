# CLAUDE.md

> 中文版本请参阅 [CLAUDE_zh.md](CLAUDE_zh.md)

## Project Overview

**dicraft-distributed-lock** is a minimal, zero-intrusion Spring Boot starter that provides declarative distributed locking via a single `@DistributeLock` annotation, backed by Redisson. It targets Java 8+, Spring Boot 2.x/3.x, and publishes to Maven Central under `cn.dicraft:dicraft-framework-spi-lock`.

## Build & Test Commands

```bash
mvn clean test                    # Run all tests
mvn clean package -DskipTests     # Build JAR without tests
mvn clean package                 # Build with tests
```

## Tech Stack

| Layer | Technology | Notes |
|-------|-----------|-------|
| Language | Java 8 | Source/target level 8 in compiler plugin |
| Build | Maven 3.6+ | No wrapper (`.mvn/` is empty) |
| DI/AOP | Spring Context 5.3.39 + AspectJ 1.9.22.1 | `provided` scope |
| Auto-config | Spring Boot Autoconfigure 2.7.18 | Compatible with Boot 3.x via `AutoConfiguration.imports` |
| Lock engine | Redisson 3.38.1 | `provided` scope |
| Utilities | Apache Commons Lang3 3.18.0 | Only non-provided compile dependency |
| Boilerplate | Lombok 1.18.28 | `provided` scope |
| Testing | JUnit 5.10 + Mockito 4.11 + AssertJ 3.24 | |

## Architecture

Single-module Maven project. Package layout by responsibility:

```
cn.dicraft
  ├── DistributeLockConfigConstant    # Sentinel values, defaults (UNSET, -1, "")
  ├── annotation/
  │   └── DistributeLock              # @interface: scene, key, keys, leaseTime, waitTime
  ├── aspect/
  │   └── DistributeLockAspect        # @Around advice: lock acquisition, key resolution, release
  ├── config/
  │   ├── DistributeLockAutoConfiguration   # Conditional bean registration
  │   └── DistributeLockProperties         # @ConfigurationProperties holder
  └── exception/
      └── DistributeLockException     # extends RuntimeException
```

Auto-config registration exists in both:
- `META-INF/spring.factories` (Spring Boot 2.x)
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Spring Boot 3.x)

## Coding Conventions

### Naming
- Classes: `PascalCase` (`DistributeLockAspect`)
- Methods/variables: `camelCase` (`analyseKeyExpression`, `resolveKeyPrefix`)
- Constants: `UPPER_SNAKE_CASE` (`UNSET`, `DEFAULT_LEASE_TIME`)
- Package: `cn.dicraft.<responsibility>` (Chinese domain convention)

### Formatting
- 4-space indentation, K&R braces (opening brace on same line)
- No wildcard imports — all classes imported explicitly
- Public members before private, with a comment separator (e.g. `// ===== section =====`) in test files

### Documentation
- **Every public class, public method, and annotation attribute has Javadoc** in English
- Javadoc uses `{@code}`, `{@link}`, `<p>`, `<ul>/<li>`, `<pre>` tags
- Author tag: `@author 烛远`
- All log messages and comments are in English
- Log messages use a consistent `[DistributeLock]` prefix for greppability

### Patterns
- **Lombok**: `@Slf4j`, `@RequiredArgsConstructor`, `@Getter` on all classes
- **Constructor injection**: fields are `private final`, injected via Lombok-generated constructor
- **Guard clauses**: early return for null/empty checks
- **Fail-fast**: throw `DistributeLockException` immediately on lock acquisition failure
- **try/finally**: lock release always in `finally` block
- **@Order(Integer.MIN_VALUE)**: aspect runs with highest precedence
- **Conditional beans**: `@ConditionalOnBean`, `@ConditionalOnMissingBean`, `@ConditionalOnClass`
- Dependencies are `provided` scope except `commons-lang3` (this is a starter library, not a fat JAR)
- Constants class has a private constructor to prevent instantiation

### Three-tier config priority (used consistently)
Annotation value > Global config (`dicraft.lock.*`) > Hardcoded default (`-1`)

## Testing Conventions

- Test class name = production class + `Test` suffix
- `@ExtendWith(MockitoExtension.class)` for unit tests
- `ApplicationContextRunner` for auto-configuration integration tests
- `@Mock` for dependencies, method names describe the scenario (e.g. `withRedissonClientBean_allBeansRegistered`)
- Assertions use AssertJ (`assertThat(...).isEqualTo(...)`) — not JUnit assertions
- `@SuppressWarnings("unused")` on test stub methods that exist only for reflection
- An inner `StubMethods` class holds annotated methods for the aspect to intercept
- Test sections separated by `// ===== Section Name =====` comments
- Helper methods for setup: `mockMethod()`, `mockLockHeld()`, `createAspect()`

## Git Conventions

- **Branch strategy**: `master` (stable), `develop` (main dev), `feature/*`, `fix/*` — branch from `develop`
- **Commit format**: Conventional Commits — `<type>: <short description>`
  - Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`
  - Imperative mood, lowercase, descriptive
  - Multi-line body with `-` bullet points for significant changes
- **PR target**: always `develop`, never `master` directly
- **Changelog**: Keep a Changelog format in `CHANGELOG.md`

## CI/CD

GitHub Actions workflow (`release.yml`) triggers on `release.published`:
- Checkout → Setup JDK 8 (Temurin) → `mvn clean package -DskipTests` → Upload JAR to release
- `release` Maven profile adds GPG signing + Sonatype Central Publishing

## Scope Rules

- This is a **library**, not an application — all runtime dependencies are `provided`
- Private constructor on utility/constant classes
- No dependencies added without clear justification
- Backward compatibility is important — Spring Boot 2.x support is retained alongside 3.x
