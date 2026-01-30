package sh.pcx.xinventories.api

import sh.pcx.xinventories.internal.model.InventoryTemplate
import sh.pcx.xinventories.internal.model.TemplateApplyTrigger
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * API for managing inventory templates.
 *
 * Templates are preset inventories that can be applied to players when
 * entering groups or on demand via commands.
 *
 * Access via [XInventoriesAPI.templates].
 */
interface TemplateAPI {

    /**
     * Gets a template by name.
     *
     * @param name The template name
     * @return The template, or null if not found
     */
    fun getTemplate(name: String): InventoryTemplate?

    /**
     * Gets all available templates.
     *
     * @return List of all templates
     */
    fun getAllTemplates(): List<InventoryTemplate>

    /**
     * Creates a new template from a player's current inventory.
     *
     * @param name The template name (must be unique)
     * @param player The player whose inventory to copy
     * @param displayName Optional display name
     * @param description Optional description
     * @return Result containing the created template or failure reason
     */
    fun createTemplate(
        name: String,
        player: Player,
        displayName: String? = null,
        description: String? = null
    ): CompletableFuture<Result<InventoryTemplate>>

    /**
     * Creates an empty template.
     *
     * @param name The template name (must be unique)
     * @return Result containing the created template or failure reason
     */
    fun createEmptyTemplate(name: String): CompletableFuture<Result<InventoryTemplate>>

    /**
     * Deletes a template.
     *
     * @param name The template name
     * @return true if deleted, false if not found
     */
    fun deleteTemplate(name: String): CompletableFuture<Boolean>

    /**
     * Applies a template to a player.
     *
     * @param player The target player
     * @param templateName The template to apply
     * @param clearInventoryFirst Whether to clear inventory before applying
     * @return Result indicating success or failure
     */
    fun applyTemplate(
        player: Player,
        templateName: String,
        clearInventoryFirst: Boolean = true
    ): CompletableFuture<Result<Unit>>

    /**
     * Applies a template to a player with a specific trigger reason.
     *
     * @param player The target player
     * @param template The template to apply
     * @param trigger The reason for applying
     * @param clearInventoryFirst Whether to clear inventory before applying
     * @return Result indicating success or failure
     */
    fun applyTemplate(
        player: Player,
        template: InventoryTemplate,
        trigger: TemplateApplyTrigger,
        clearInventoryFirst: Boolean = true
    ): CompletableFuture<Result<Unit>>

    /**
     * Checks if a player has joined a group for the first time.
     *
     * @param playerUuid The player's UUID
     * @param groupName The group name
     * @return true if this is their first join
     */
    fun isFirstJoin(playerUuid: UUID, groupName: String): Boolean

    /**
     * Marks a player as having joined a group.
     *
     * @param playerUuid The player's UUID
     * @param groupName The group name
     */
    fun markJoined(playerUuid: UUID, groupName: String)

    /**
     * Resets a player's first-join status for a group.
     *
     * @param playerUuid The player's UUID
     * @param groupName The group name (or null for all groups)
     */
    fun resetFirstJoin(playerUuid: UUID, groupName: String? = null)

    /**
     * Saves a template (creates or updates).
     *
     * @param template The template to save
     * @return true if saved successfully
     */
    fun saveTemplate(template: InventoryTemplate): CompletableFuture<Boolean>

    /**
     * Reloads all templates from disk.
     */
    fun reloadTemplates(): CompletableFuture<Unit>
}
