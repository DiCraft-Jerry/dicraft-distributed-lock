# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Unit tests for multi-key locking (`keys` attribute): sorted resolution, blank filtering, Collection/Array values, prefix integration, precedence over `key`

### Changed

- Cache `SpelExpressionParser` and `DefaultParameterNameDiscoverer` as `static final` fields to avoid repeated instantiation
- Add warning log when method parameter names cannot be discovered, guiding users to compile with `-parameters` flag

## [1.0.1] - 2026-03-11

### Added

- Global lock key prefix via `dicraft.lock.key-prefix` configuration property
  - When configured, all lock keys are prefixed with `{prefix}:` (e.g. `my-app:order#123`)
  - Prevents key collisions across multiple microservices sharing the same Redis instance
  - No prefix added when not configured, preserving backward compatibility
- Multi-key lock support via `keys` annotation attribute
  - Each SpEL expression evaluated independently, results sorted lexicographically and joined with `.`
  - Takes precedence over single `key` attribute
  - Deterministic key ordering reduces deadlock risk when locking multiple resources
- Spring Boot 3.x auto-configuration compatibility via `AutoConfiguration.imports`
  - Existing `spring.factories` retained for Spring Boot 2.x backward compatibility

## [1.0.0] - 2025-03-10

### Added

- `@DistributeLock` annotation for declarative distributed locking
- SpEL expression support for dynamic lock key resolution
- Annotation-level and global-level configuration for `leaseTime` and `waitTime`
- Three-tier configuration priority: annotation > global config > default
- Redisson Watchdog auto-renewal enabled by default
- Full lifecycle logging for lock acquisition, hold time and release
- Spring Boot auto-configuration via `spring.factories`
- `DistributeLockException` thrown when lock acquisition fails
