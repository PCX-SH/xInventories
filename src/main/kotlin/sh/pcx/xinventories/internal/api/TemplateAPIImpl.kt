package sh.pcx.xinventories.internal.api

import kotlinx.coroutines.runBlocking
import org.bukkit.entity.Player
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.api.TemplateAPI
import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.internal.model.InventoryTemplate
import sh.pcx.xinventories.internal.model.TemplateApplyTrigger
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Implementation of the TemplateAPI.
 * Adapts internal TemplateService to the public API interface.
 */
class TemplateAPIImpl(private val plugin: PluginContext) : TemplateAPI {

    private val templateService get() = plugin.serviceManager.templateService
    private val groupService get() = plugin.serviceManager.groupService

    override fun getTemplate(name: String): InventoryTemplate? {
        return templateService.getTemplate(name)
    }

    override fun getAllTemplates(): List<InventoryTemplate> {
        return templateService.getAllTemplates()
    }

    override fun createTemplate(
        name: String,
        player: Player,
        displayName: String?,
        description: String?
    ): CompletableFuture<Result<InventoryTemplate>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                templateService.createTemplate(name, player, displayName, description)
            }
        }
    }

    override fun createEmptyTemplate(name: String): CompletableFuture<Result<InventoryTemplate>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                try {
                    val template = InventoryTemplate.empty(name)
                    val saved = templateService.saveTemplate(template)
                    if (saved) {
                        Result.success(template)
                    } else {
                        Result.failure(Exception("Failed to save template"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override fun deleteTemplate(name: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                templateService.deleteTemplate(name)
            }
        }
    }

    override fun applyTemplate(
        player: Player,
        templateName: String,
        clearInventoryFirst: Boolean
    ): CompletableFuture<Result<Unit>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                try {
                    val template = templateService.getTemplate(templateName)
                        ?: return@runBlocking Result.failure(Exception("Template not found: $templateName"))

                    val group = groupService.getGroupForWorld(player.world).toApiModel()
                    val success = templateService.applyTemplate(
                        player,
                        template,
                        group,
                        TemplateApplyTrigger.MANUAL,
                        clearInventoryFirst
                    )

                    if (success) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("Failed to apply template"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override fun applyTemplate(
        player: Player,
        template: InventoryTemplate,
        trigger: TemplateApplyTrigger,
        clearInventoryFirst: Boolean
    ): CompletableFuture<Result<Unit>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                try {
                    val group = groupService.getGroupForWorld(player.world).toApiModel()
                    val success = templateService.applyTemplate(
                        player,
                        template,
                        group,
                        trigger,
                        clearInventoryFirst
                    )

                    if (success) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("Failed to apply template"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override fun isFirstJoin(playerUuid: UUID, groupName: String): Boolean {
        return templateService.isFirstJoin(playerUuid, groupName)
    }

    override fun markJoined(playerUuid: UUID, groupName: String) {
        templateService.markJoined(playerUuid, groupName)
    }

    override fun resetFirstJoin(playerUuid: UUID, groupName: String?) {
        // The internal service doesn't have a reset method, but we can add this behavior
        // For now, this is a no-op as it requires modifying the internal first-join tracker
        // which would need additional implementation in TemplateService
    }

    override fun saveTemplate(template: InventoryTemplate): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                templateService.saveTemplate(template)
            }
        }
    }

    override fun reloadTemplates(): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                templateService.reload()
            }
        }
    }
}
