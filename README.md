# xInventories

<p align="center">
  <img src="https://raw.githubusercontent.com/PCX-SH/xInventories/main/images/BANNER.png" alt="xInventories Banner">
</p>

A powerful per-world inventory management plugin for Paper 1.21.1+ servers. Separate player inventories, experience, health, and more across different worlds or world groups.

## Features

- **Per-World Inventories** - Separate inventories for different worlds or world groups
- **Per-GameMode Inventories** - Optionally separate inventories by gamemode (Survival, Creative, etc.)
- **Multiple Storage Backends** - YAML (default), SQLite, or MySQL
- **High-Performance Caching** - Caffeine-powered write-behind caching for optimal performance
- **Backup & Restore** - Create and restore backups of all player data
- **Data Migration** - Migrate between storage backends seamlessly
- **Admin GUI** - Visual interface for managing groups, worlds, and viewing player data
- **Regex Patterns** - Match worlds using regex patterns (e.g., `dungeon_.*`)
- **Full API** - Comprehensive developer API with events and subscriptions
- **PlaceholderAPI Support** - Built-in placeholders for displaying inventory info

## Requirements

- **Paper 1.21.1+** (not compatible with Spigot)
- **Java 21+**

## Installation

1. Download `xInventories-1.0.1.jar`
2. Place in your server's `plugins/` folder
3. Restart your server
4. Configure `plugins/xInventories/config.yml` as needed
5. Set up world groups in `plugins/xInventories/groups.yml`

## Commands

All commands use `/xinventories` (aliases: `/xinv`, `/xi`)

| Command | Description | Permission |
|---------|-------------|------------|
| `/xinv` | Show help menu | `xinventories.command` |
| `/xinv reload` | Reload configuration | `xinventories.command.reload` |
| `/xinv save [player]` | Save player inventory | `xinventories.command.save` |
| `/xinv load <player> [group]` | Load player inventory | `xinventories.command.load` |
| `/xinv gui` | Open admin GUI | `xinventories.gui` |
| `/xinv group <list\|info\|create\|delete\|setdefault> [name]` | Manage groups | `xinventories.command.group` |
| `/xinv world <assign\|unassign\|list> [world] [group]` | Manage world assignments | `xinventories.command.world` |
| `/xinv pattern <add\|remove\|list> <group> [pattern]` | Manage regex patterns | `xinventories.command.pattern` |
| `/xinv cache <stats\|clear> [player]` | Manage cache | `xinventories.command.cache` |
| `/xinv backup <create\|restore\|list\|delete> [name/id]` | Manage backups | `xinventories.command.backup` |
| `/xinv convert <from> <to>` | Migrate storage (yaml/sqlite/mysql) | `xinventories.command.convert` |
| `/xinv debug [info\|player\|storage] [player]` | Debug information | `xinventories.command.debug` |

## Permissions

### Admin Permission
| Permission | Description | Default |
|------------|-------------|---------|
| `xinventories.admin` | Full admin access (includes all below) | op |

### Command Permissions
| Permission | Description | Default |
|------------|-------------|---------|
| `xinventories.command` | Use base command | true |
| `xinventories.command.reload` | Reload configuration | op |
| `xinventories.command.save` | Save inventories | op |
| `xinventories.command.save.others` | Save other players | op |
| `xinventories.command.load` | Load inventories | op |
| `xinventories.command.group` | Manage groups | op |
| `xinventories.command.world` | Manage worlds | op |
| `xinventories.command.pattern` | Manage patterns | op |
| `xinventories.command.cache` | Manage cache | op |
| `xinventories.command.backup` | Manage backups | op |
| `xinventories.command.convert` | Migrate storage | op |
| `xinventories.command.debug` | Debug commands | op |
| `xinventories.gui` | Open admin GUI | op |

### Bypass Permissions
| Permission | Description | Default |
|------------|-------------|---------|
| `xinventories.bypass` | Bypass all inventory switching | false |
| `xinventories.bypass.<group>` | Bypass switching for specific group | false |

## Configuration

### config.yml

```yaml
# Storage settings
storage:
  # Storage type: YAML, SQLITE, or MYSQL
  type: YAML

  # SQLite settings (if type is SQLITE)
  sqlite:
    file: "data/xinventories.db"

  # MySQL settings (if type is MYSQL)
  mysql:
    host: "localhost"
    port: 3306
    database: "xinventories"
    username: "root"
    password: ""
    pool:
      maximumPoolSize: 10
      minimumIdle: 2
      connectionTimeout: 30000
      idleTimeout: 600000
      maxLifetime: 1800000

# Cache settings
cache:
  enabled: true
  maxSize: 1000
  expireAfterAccessMinutes: 30
  expireAfterWriteMinutes: 60
  writeBehindSeconds: 30

# Feature toggles
features:
  asyncSaving: true
  asyncLoading: true
  logInventorySwitches: false
  adminNotifications: true

# Performance settings
performance:
  threadPoolSize: 4

# Backup settings
backup:
  directory: "backups"
  maxBackups: 10
  autoBackup: false
  autoBackupIntervalHours: 24
```

### groups.yml

```yaml
# Default group - catches all worlds not assigned elsewhere
default:
  default: true
  priority: 0
  worlds: []
  patterns: []
  settings:
    saveHealth: true
    saveHunger: true
    saveSaturation: true
    saveExhaustion: true
    saveExperience: true
    savePotionEffects: true
    saveEnderChest: true
    saveGameMode: false
    separateGameModeInventories: true
    clearOnDeath: false
    clearOnJoin: false

# Example: Survival worlds group
survival:
  priority: 10
  worlds:
    - "world"
    - "world_nether"
    - "world_the_end"
  patterns: []
  settings:
    separateGameModeInventories: true
    saveEnderChest: true

# Example: Creative worlds group
creative:
  priority: 10
  worlds:
    - "creative"
    - "plots"
  patterns:
    - "creative_.*"
  settings:
    separateGameModeInventories: false
    saveEnderChest: false

# Example: Minigames using regex pattern
minigames:
  priority: 20
  worlds: []
  patterns:
    - "game_.*"
    - "arena_.*"
  settings:
    saveHealth: false
    saveHunger: false
    saveExperience: false
    savePotionEffects: false
    clearOnJoin: true
```

### Group Settings

| Setting | Description | Default |
|---------|-------------|---------|
| `saveHealth` | Save/restore player health | `true` |
| `saveHunger` | Save/restore food level | `true` |
| `saveSaturation` | Save/restore saturation | `true` |
| `saveExhaustion` | Save/restore exhaustion | `true` |
| `saveExperience` | Save/restore XP and levels | `true` |
| `savePotionEffects` | Save/restore potion effects | `true` |
| `saveEnderChest` | Save/restore ender chest | `true` |
| `saveGameMode` | Restore gamemode on switch | `false` |
| `separateGameModeInventories` | Separate inventories per gamemode | `true` |
| `clearOnDeath` | Clear inventory on death | `false` |
| `clearOnJoin` | Clear inventory when entering group | `false` |

### messages.yml

Customize all plugin messages using MiniMessage format for colors and formatting.

```yaml
prefix: "<gray>[<aqua>xInventories</aqua>]</gray> "

messages:
  no-permission: "<red>You don't have permission to do that."
  player-not-found: "<red>Player <yellow>{player}</yellow> not found."
  reload-success: "<green>Configuration reloaded successfully."
  # ... more messages
```

## Storage Backends

### YAML (Default)
- Stores data in `plugins/xInventories/data/players/`
- One file per player UUID
- Human-readable, easy to edit manually
- Best for small to medium servers

### SQLite
- Single database file
- No external dependencies
- Better performance than YAML for larger datasets
- Good for medium servers

### MySQL
- External database server
- Best performance at scale
- Required for multi-server setups (BungeeCord/Velocity networks)
- Includes HikariCP connection pooling

### Migrating Storage

```
/xinv convert yaml sqlite    # YAML to SQLite
/xinv convert sqlite mysql   # SQLite to MySQL
/xinv convert mysql yaml     # MySQL to YAML
```

## PlaceholderAPI Placeholders

| Placeholder | Description |
|-------------|-------------|
| `%xinventories_group%` | Player's current inventory group |
| `%xinventories_bypass%` | Whether player has bypass enabled |
| `%xinventories_groups_count%` | Total number of configured groups |
| `%xinventories_storage_type%` | Current storage backend type |
| `%xinventories_cache_size%` | Current cache entry count |
| `%xinventories_cache_max%` | Maximum cache size |
| `%xinventories_cache_hit_rate%` | Cache hit rate percentage |
| `%xinventories_version%` | Plugin version |

## Developer API

### Getting the API

```kotlin
// Kotlin
val api = XInventoriesProvider.get()

// Java
XInventoriesAPI api = XInventoriesProvider.get();
```

### Example: Get Player's Current Group

```kotlin
val group = api.getGroupForWorld(player.world)
println("Player is in group: ${group.name}")
```

### Example: Save/Load Inventory

```kotlin
// Save current inventory
api.savePlayerData(player).thenAccept { result ->
    result.onSuccess { println("Saved!") }
    result.onFailure { println("Failed: ${it.message}") }
}

// Load from specific group
api.loadPlayerData(player, "creative").thenAccept { result ->
    result.onSuccess { println("Loaded!") }
}
```

### Example: Subscribe to Events

```kotlin
val subscription = api.onInventorySwitch { context ->
    println("${context.player.name} switched from ${context.fromGroup} to ${context.toGroup}")
}

// Later, unsubscribe
subscription.unsubscribe()
```

### Dependency

```kotlin
// build.gradle.kts
compileOnly(files("libs/xInventories-1.0.1.jar"))
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>sh.pcx</groupId>
    <artifactId>xInventories</artifactId>
    <version>1.0.1</version>
    <scope>provided</scope>
</dependency>
```

Make sure to add `xInventories` as a dependency or soft-dependency in your `plugin.yml`:

```yaml
depend: [xInventories]
# or
softdepend: [xInventories]
```

## Screenshots

<details>
<summary>Click to view screenshots</summary>

### Main Admin GUI
![Main GUI](https://raw.githubusercontent.com/PCX-SH/xInventories/main/images/GUI_MAIN.png)

### Group Management
![Groups GUI](https://raw.githubusercontent.com/PCX-SH/xInventories/main/images/GUI_GROUPS.png)

### Player Inventory Viewer
![Player GUI](https://raw.githubusercontent.com/PCX-SH/xInventories/main/images/GUI_PLAYER.png)

</details>

## Testing

xInventories includes a comprehensive test suite to ensure reliability and prevent regressions.

### Test Coverage

| Category | Tests | Description |
|----------|-------|-------------|
| **Unit Tests** | 400+ | Serializers, models, cache, configuration |
| **Integration Tests** | 500+ | Storage backends, services, API |
| **Total** | **991 passing** | 83 skipped (MockBukkit limitations) |

### Running Tests

```bash
# Run all tests
./gradlew test

# Run with detailed output
./gradlew test --info

# View HTML report
# build/reports/tests/test/index.html
```

### Test Framework

- **JUnit 5** - Test framework
- **MockK** - Kotlin mocking library
- **MockBukkit** - Bukkit API mocking for Paper 1.21
- **kotlinx-coroutines-test** - Coroutine testing utilities

### Test Categories

- **Serializers** - ItemStack, PlayerData, PotionEffect serialization/deserialization
- **Models** - Group, WorldPattern, PlayerData data classes
- **Cache** - Caffeine cache operations, dirty tracking, eviction
- **Configuration** - Config loading, validation, defaults
- **Storage** - YAML, SQLite storage operations and roundtrips
- **Services** - GroupService, StorageService, InventoryService, BackupService, MigrationService
- **API** - Public API methods, event handling, subscriptions

## FAQ

**Q: Does this work with Spigot?**
A: No, xInventories requires Paper 1.21.1 or higher. It uses Paper-specific APIs and the native Adventure library.

**Q: How do I share inventories between servers?**
A: Use MySQL storage and configure all servers to use the same database.

**Q: Can I have different inventories for Creative and Survival in the same world?**
A: Yes! Enable `separateGameModeInventories: true` in the group settings.

**Q: How do I exclude certain players from inventory switching?**
A: Give them the `xinventories.bypass` permission, or `xinventories.bypass.<groupname>` for specific groups.

**Q: My players lost their inventory!**
A: Check `/xinv debug player <name>` for their data. You can also restore from backup with `/xinv backup restore <id>`.

**Q: How do I match multiple worlds with similar names?**
A: Use regex patterns in groups.yml, e.g., `patterns: ["skyblock_.*"]` matches skyblock_1, skyblock_2, etc.

**Q: Can I edit player inventories through the GUI?**
A: Yes! Use `/xinv gui`, navigate to Player Management, select a player, and you can view and manage their inventories for each group.

## Support

- **Issues:** [GitHub Issues](https://github.com/PCX-SH/xInventories/issues)
- **Source:** [GitHub](https://github.com/PCX-SH/xInventories)

## License

MIT License - See [LICENSE](LICENSE) file for details.

## Author

**Reset65**
