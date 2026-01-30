# Contributing to xInventories

Thank you for your interest in contributing to xInventories! This document provides guidelines and information for contributors.

## Getting Started

### Prerequisites

- **Java 21+** (JDK, not just JRE)
- **Gradle 8.0+** (or use the wrapper when available)
- **Git**
- An IDE with Kotlin support (IntelliJ IDEA recommended)

### Setting Up the Development Environment

1. **Fork and clone the repository**
   ```bash
   git clone https://github.com/YOUR_USERNAME/xInventories.git
   cd xInventories
   ```

2. **Import into your IDE**
   - IntelliJ IDEA: File → Open → Select the project folder
   - The Gradle project will be automatically detected

3. **Build the project**
   ```bash
   gradle build
   ```

4. **Run a test server**
   ```bash
   gradle runServer
   ```
   This uses the `run-paper` plugin to spin up a Paper test server with the plugin installed.

## Project Structure

```
xInventories/
├── src/main/kotlin/sh/pcx/xinventories/
│   ├── XInventories.kt              # Main plugin class
│   ├── api/                         # Public developer API
│   │   ├── event/                   # Custom Bukkit events
│   │   ├── model/                   # API data models
│   │   └── exception/               # API exceptions
│   └── internal/                    # Internal implementation
│       ├── cache/                   # Caching layer (Caffeine)
│       ├── command/                 # Command system
│       ├── config/                  # Configuration handling
│       ├── gui/                     # GUI menus
│       ├── hook/                    # Third-party integrations
│       ├── listener/                # Event listeners
│       ├── model/                   # Internal data models
│       ├── service/                 # Business logic services
│       ├── storage/                 # Storage backends
│       └── util/                    # Utilities
└── src/main/resources/
    ├── plugin.yml
    ├── config.yml
    ├── groups.yml
    └── messages.yml
```

### Key Architectural Concepts

- **API vs Internal**: The `api` package is the public developer API. The `internal` package contains implementation details that should not be accessed by other plugins.
- **Services**: Business logic is organized into services (e.g., `InventoryService`, `GroupService`).
- **Storage abstraction**: All storage backends implement the `Storage` interface.
- **Coroutines**: Async operations use Kotlin coroutines with custom dispatchers.

## Code Style

### Kotlin Guidelines

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Prefer immutable data (`val` over `var`, data classes)
- Use `internal` visibility for implementation details
- Document public API with KDoc comments

### Formatting

- 4 spaces for indentation (no tabs)
- Max line length: 120 characters
- Use trailing commas in multi-line lists
- Blank line between functions

### Example

```kotlin
/**
 * Saves the player's inventory to storage.
 *
 * @param player The player whose inventory to save
 * @param group Optional group override
 * @return Result indicating success or failure
 */
suspend fun saveInventory(
    player: Player,
    group: String? = null,
): Result<Unit> {
    val targetGroup = group ?: groupService.getGroupForWorld(player.world).name

    return runCatching {
        val snapshot = PlayerInventorySnapshot.fromPlayer(player, targetGroup)
        storageService.save(snapshot)
    }
}
```

## Making Changes

### Branch Naming

- `feature/` - New features (e.g., `feature/vault-integration`)
- `fix/` - Bug fixes (e.g., `fix/inventory-duplication`)
- `docs/` - Documentation changes
- `refactor/` - Code refactoring

### Commit Messages

Write clear, concise commit messages:

```
Add MySQL connection pooling configuration

- Add pool settings to MainConfig
- Implement HikariCP configuration
- Add config.yml documentation
```

### Pull Request Process

1. **Create a feature branch** from `main`
2. **Make your changes** with clear commits
3. **Test thoroughly** on a Paper 1.20.5+ server
4. **Update documentation** if needed (README, config comments)
5. **Submit a pull request** with:
   - Clear description of changes
   - Screenshots for GUI changes
   - Testing steps performed

## Testing

### Manual Testing Checklist

Before submitting a PR, verify:

- [ ] Plugin loads without errors
- [ ] Commands work as expected
- [ ] GUI menus function correctly
- [ ] Inventory switching works between worlds
- [ ] Data persists after server restart
- [ ] No console errors or warnings (except deprecation warnings from Bukkit APIs)

### Test Scenarios

- Join server → inventory loads correctly
- Switch worlds → inventory switches to correct group
- Change gamemode → inventory switches (if enabled)
- Reload plugin → configuration reloads without data loss
- Stop server → all player data saves

## Reporting Issues

### Bug Reports

Include:
- Paper version and build number
- xInventories version
- Steps to reproduce
- Expected vs actual behavior
- Console errors (full stack trace)
- Relevant configuration

### Feature Requests

Include:
- Clear description of the feature
- Use case / why it's needed
- Proposed implementation (optional)

## Areas for Contribution

### Good First Issues

- Adding new PlaceholderAPI placeholders
- Improving error messages
- Adding configuration validation
- Documentation improvements

### Larger Projects

- Additional storage backend support
- Migration tools from other inventory plugins
- Performance optimizations
- Localization support

## Code of Conduct

- Be respectful and constructive
- Welcome newcomers
- Focus on the code, not the person
- Assume good intentions

## Questions?

- Open a [GitHub Discussion](https://github.com/PCX-SH/xInventories/discussions)
- Check existing issues for similar questions

## License

By contributing, you agree that your contributions will be licensed under the MIT License.

---

Thank you for contributing to xInventories!
