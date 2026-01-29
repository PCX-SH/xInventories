# Changelog

All notable changes to xInventories will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-01-29

### Added
- Comprehensive test suite with 991 passing tests
  - Unit tests for serializers, models, cache, and configuration
  - Integration tests for storage backends, services, and API
- Test framework dependencies (JUnit 5, MockK, MockBukkit, kotlinx-coroutines-test)
- SQLite JDBC driver for test environment
- Testing documentation section in README

### Fixed
- Fixed null pointer exception in `InventoryService` when using null group for global bypass
  - Changed internal set implementation to support null values for global bypass tracking
- Fixed `WorldPattern` equality comparison
  - Two patterns with the same regex string now correctly compare as equal
  - Added proper `equals()` and `hashCode()` implementations

## [1.0.0] - 2026-01-28

### Added
- Initial release
- Per-world inventory management
- Per-gamemode inventory separation
- Multiple storage backends (YAML, SQLite, MySQL)
- Caffeine-powered write-behind caching
- Backup and restore functionality
- Data migration between storage backends
- Admin GUI for management
- Regex pattern matching for worlds
- Full developer API with events
- PlaceholderAPI integration
- Comprehensive command system
- MiniMessage support for all messages
