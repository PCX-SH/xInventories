# Changelog

All notable changes to xInventories will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-01-30

### Added

#### Data Foundation
- **Inventory Versioning** - Automatic version history for player inventories
  - Configurable version retention and triggers (save, world-change, gamemode-change)
  - Commands: `/xinv version list`, `/xinv version restore`, `/xinv version diff`
  - Rollback to any previous version with preview
- **Death Recovery** - Recover inventories lost on death
  - Configurable retention period and max records per player
  - Commands: `/xinv death list`, `/xinv death restore`, `/xinv death preview`
  - Automatic cleanup of old records

#### Content Control
- **Templates** - Pre-defined inventory templates for groups
  - Apply templates on first join or group entry
  - Commands: `/xinv template list`, `/xinv template apply`, `/xinv template create`
  - Support for starter kits, event loadouts, etc.
- **Item Restrictions** - Control which items players can use per group
  - Whitelist or blacklist modes with material patterns
  - Actions: prevent, drop, or clear restricted items
  - Per-group restriction configurations
- **Shared Slots** - Share specific inventory slots across groups
  - Configure shared slots, armor slots, or offhand
  - Real-time sync across group transitions

#### Advanced Groups
- **Conditional Groups** - Dynamic group assignment based on conditions
  - Permission-based: `permission: "group.vip"`
  - Schedule-based: Time ranges with timezone support
  - Cron-based: Complex schedules like "weekends only"
  - PlaceholderAPI: Evaluate placeholders for group selection
- **Inventory Locking** - Temporarily lock player inventories
  - Commands: `/xinv lock`, `/xinv unlock`, `/xinv lock list`
  - Configurable duration, scope (all/group/slots), and bypass permissions
  - Events: `InventoryLockEvent`, `InventoryUnlockEvent`

#### External Integrations
- **Vault Economy** - Per-group economy balances
  - Separate balances per inventory group
  - Transfer money between groups
  - Full Vault provider compatibility
- **Plugin Import** - Import data from other inventory plugins
  - PerWorldInventory (PWI) support
  - MultiVerse-Inventories (MVI) support
  - MyWorlds support
  - Commands: `/xinv import detect`, `/xinv import <plugin>`

#### Network Sync
- **Cross-Server Sync** - Redis-based synchronization for networks
  - Real-time inventory sync across BungeeCord/Velocity networks
  - Distributed locking to prevent data conflicts
  - Configurable conflict resolution strategies (latest-wins, merge)
  - Server heartbeat monitoring
  - Cache invalidation across servers

### Changed
- Updated test suite to 1578 tests (up from 991)
- Added `version` field to PlayerData for sync conflict detection
- Added `balances` field to PlayerData for per-group economy
- Enhanced CronExpression to properly handle day-of-week wildcards
- Changed `mainThreadDispatcher` property type to `CoroutineDispatcher` for better testability

### Fixed
- Fixed CronExpression day matching when only day-of-week is specified
- Fixed SQLite batch save missing `version` and `balances` columns
- Fixed EconomyService mock configuration in tests
- Fixed YAML deserialization compatibility with MockBukkit 4.101.0
  - Added recursive `MemorySection` to `Map` conversion in `InventorySerializer`
- Fixed Paper 1.21.11 API compatibility
  - Updated `GENERIC_MAX_HEALTH` to `MAX_HEALTH` attribute references
- Fixed coroutine context handling in integration tests
- Fixed scheduler timing in gamemode change tests

### Dependencies
- Gradle 9.3.0
- Kotlin 2.1.21
- kotlinx-coroutines 1.9.0
- kotlinx-serialization-json 1.8.1
- Paper API 1.21.11-R0.1-SNAPSHOT
- Shadow plugin 9.3.1
- run-paper plugin 3.0.2
- Caffeine 3.2.3
- HikariCP 6.2.1
- Jedis 5.2.0
- JUnit Jupiter 5.13.4
- MockK 1.14.7
- MockBukkit 4.101.0
- SQLite JDBC 3.51.1.0
- bStats 3.1.0
- Embedded Redis 1.4.3

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
