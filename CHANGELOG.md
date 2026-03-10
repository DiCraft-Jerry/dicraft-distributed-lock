# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

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
