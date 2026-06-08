# Contributing to dicraft-distributed-lock

[中文版](CONTRIBUTING_zh.md)

Thank you for considering contributing! Here are the guidelines to help you get started.

## Development Environment

- **JDK**: 1.8+
- **Build Tool**: Maven 3.6+
- **IDE**: IntelliJ IDEA recommended

```bash
git clone https://github.com/DiCraft-Jerry/dicraft-distributed-lock.git
cd dicraft-distributed-lock
mvn clean test
```

## Branch Strategy

| Branch | Purpose |
|--------|---------|
| `master` | Stable releases only |
| `develop` | Main development branch |
| `feature/*` | New features, branched from `develop` |
| `fix/*` | Bug fixes, branched from `develop` |

## How to Contribute

1. **Fork** the repository
2. Create a feature branch from `develop`: `git checkout -b feature/your-feature develop`
3. Make your changes and add tests
4. Run `mvn clean test` to ensure all tests pass
5. Commit with a clear message (see below)
6. Push to your fork and open a **Pull Request** against `develop`

## Commit Message Convention

Follow the [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>: <short summary>

<optional body>
```

**Types**: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`

Examples:
- `feat: add lock timeout callback support`
- `fix: resolve SpEL parsing failure for nested objects`
- `test: add concurrency tests for tryLock`

## Code Style

- Follow standard Java coding conventions
- All public APIs must have Javadoc in English
- Keep comments and log messages in English
- Avoid adding unnecessary dependencies
- Cache thread-safe singletons (e.g. `SpelExpressionParser`, `DefaultParameterNameDiscoverer`) as `static final` fields rather than creating new instances per invocation

## Reporting Issues

- Use [GitHub Issues](https://github.com/dicraft/dicraft-distributed-lock/issues) to report bugs or request features
- Include steps to reproduce, expected behavior, and actual behavior for bug reports
- Check existing issues before creating a new one

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
