package sh.pcx.xinventories.internal.config.migrations

import org.bukkit.configuration.file.YamlConfiguration
import sh.pcx.xinventories.internal.config.ConfigMigration
import sh.pcx.xinventories.internal.util.Logging

/**
 * Example migration from config version 1 to version 2.
 *
 * This migration serves as a template for future migrations.
 * When actual breaking changes are made, implement the migration logic here.
 *
 * Example changes this might handle:
 * - Rename configuration keys
 * - Restructure nested configurations
 * - Add default values for new required fields
 * - Convert data formats
 */
object Migration_1_to_2 : ConfigMigration {
    override val fromVersion: Int = 1
    override val toVersion: Int = 2

    override fun migrate(config: YamlConfiguration): YamlConfiguration {
        Logging.debug { "Running Migration_1_to_2" }

        // Example migration operations (commented out as placeholders):

        // 1. Rename a key
        // renameKey(config, "old.key.path", "new.key.path")

        // 2. Move a value to a new location
        // moveValue(config, "source.path", "destination.path")

        // 3. Add a new required field with default value
        // if (!config.contains("new.required.field")) {
        //     config.set("new.required.field", "default_value")
        // }

        // 4. Convert a value format
        // val oldValue = config.getString("some.value")
        // if (oldValue != null) {
        //     config.set("some.value", convertFormat(oldValue))
        // }

        // 5. Remove deprecated keys
        // config.set("deprecated.key", null)

        return config
    }

    /**
     * Helper function to rename a configuration key while preserving the value.
     */
    @Suppress("unused")
    private fun renameKey(config: YamlConfiguration, oldPath: String, newPath: String) {
        val value = config.get(oldPath)
        if (value != null) {
            config.set(newPath, value)
            config.set(oldPath, null)
            Logging.debug { "Renamed config key: $oldPath -> $newPath" }
        }
    }

    /**
     * Helper function to move a value to a new location.
     */
    @Suppress("unused")
    private fun moveValue(config: YamlConfiguration, sourcePath: String, destPath: String) {
        val value = config.get(sourcePath)
        if (value != null) {
            config.set(destPath, value)
            config.set(sourcePath, null)
            Logging.debug { "Moved config value: $sourcePath -> $destPath" }
        }
    }
}
