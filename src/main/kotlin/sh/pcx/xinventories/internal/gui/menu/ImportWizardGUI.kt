package sh.pcx.xinventories.internal.gui.menu

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.ImportMapping
import sh.pcx.xinventories.internal.model.ImportOptions
import sh.pcx.xinventories.internal.model.ImportPreview
import sh.pcx.xinventories.internal.model.ImportResult
import sh.pcx.xinventories.internal.model.ImportGroup
import sh.pcx.xinventories.internal.model.ImportSource

/**
 * Multi-step import wizard GUI for importing data from other inventory plugins.
 *
 * Steps:
 * 1. Detect installed plugins (PWI, MVI, MyWorlds)
 * 2. Select source plugin
 * 3. Preview data to import (player count, groups)
 * 4. Configure group mapping
 * 5. Progress indicator during import
 * 6. Results summary
 */
class ImportWizardGUI(
    plugin: XInventories,
    private val step: ImportStep = ImportStep.DETECT_SOURCES,
    private val selectedSourceId: String? = null,
    private val mapping: ImportMapping? = null,
    private val preview: ImportPreview? = null,
    private val result: ImportResult? = null
) : AbstractGUI(
    plugin,
    getStepTitle(step),
    54
) {
    enum class ImportStep {
        DETECT_SOURCES,
        SELECT_SOURCE,
        PREVIEW,
        CONFIGURE_MAPPING,
        IMPORTING,
        RESULTS
    }

    companion object {
        private fun getStepTitle(step: ImportStep): Component {
            val stepNum = step.ordinal + 1
            val title = when (step) {
                ImportStep.DETECT_SOURCES -> "Detect Sources"
                ImportStep.SELECT_SOURCE -> "Select Source"
                ImportStep.PREVIEW -> "Preview Import"
                ImportStep.CONFIGURE_MAPPING -> "Configure Mapping"
                ImportStep.IMPORTING -> "Importing..."
                ImportStep.RESULTS -> "Import Results"
            }
            return Component.text("Import Wizard - Step $stepNum: $title", NamedTextColor.GREEN)
        }
    }

    private var detectedSources: List<ImportSource> = emptyList()

    init {
        setupStep()
    }

    private fun setupStep() {
        when (step) {
            ImportStep.DETECT_SOURCES -> setupDetectSources()
            ImportStep.SELECT_SOURCE -> setupSelectSource()
            ImportStep.PREVIEW -> setupPreview()
            ImportStep.CONFIGURE_MAPPING -> setupConfigureMapping()
            ImportStep.IMPORTING -> setupImporting()
            ImportStep.RESULTS -> setupResults()
        }
    }

    private fun setupDetectSources() {
        items.clear()
        fillBorder(GUIComponents.filler())

        // Header
        setItem(1, 4, GUIItemBuilder()
            .material(Material.SPYGLASS)
            .name("Detecting Import Sources", NamedTextColor.AQUA)
            .lore("Scanning for compatible plugins...")
            .build()
        )

        // Detect sources
        val importService = plugin.serviceManager.importService
        detectedSources = importService.detectSources()

        if (detectedSources.isEmpty()) {
            setItem(2, 4, GUIItemBuilder()
                .material(Material.BARRIER)
                .name("No Sources Found", NamedTextColor.RED)
                .lore("No compatible inventory plugins detected.")
                .lore("")
                .lore("Supported plugins:")
                .lore("- PerWorldInventory (PWI)")
                .lore("- MultiVerse-Inventories (MVI)")
                .lore("- MyWorlds")
                .build()
            )
        } else {
            setItem(2, 4, GUIItemBuilder()
                .material(Material.EMERALD)
                .name("${detectedSources.size} Source(s) Found", NamedTextColor.GREEN)
                .lore("Click 'Next' to select a source")
                .build()
            )

            // Show detected sources
            var slot = 19
            for (source in detectedSources.take(5)) {
                setItem(slot, GUIItemBuilder()
                    .material(getSourceMaterial(source.id))
                    .name(Component.text(source.name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                    .lore(Component.text(if (source.hasApiAccess) "API Available" else "File-based", NamedTextColor.GRAY))
                    .build()
                )
                slot++
            }
        }

        // Navigation
        setupStepNavigation(
            canGoBack = false,
            canGoNext = detectedSources.isNotEmpty(),
            onNext = { player ->
                ImportWizardGUI(plugin, ImportStep.SELECT_SOURCE).open(player)
            }
        )
    }

    private fun setupSelectSource() {
        items.clear()
        fillBorder(GUIComponents.filler())

        val importService = plugin.serviceManager.importService
        detectedSources = importService.detectSources()

        // Header
        setItem(0, 4, GUIItemBuilder()
            .material(Material.CHEST)
            .name("Select Import Source", NamedTextColor.AQUA)
            .lore("Choose which plugin to import from")
            .build()
        )

        // Source options
        var slot = 19
        for (source in detectedSources) {
            val isSelected = source.id == selectedSourceId

            setItem(slot, GUIItemBuilder()
                .material(getSourceMaterial(source.id))
                .name(Component.text(source.name, if (isSelected) NamedTextColor.GREEN else NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Players: ${source.getPlayers().size}", NamedTextColor.GRAY))
                .lore(Component.text("Groups: ${source.getGroups().size}", NamedTextColor.GRAY))
                .lore(Component.text(if (source.hasApiAccess) "Using API" else "Using Files", NamedTextColor.DARK_GRAY))
                .lore(Component.empty())
                .lore(if (isSelected) Component.text("SELECTED", NamedTextColor.GREEN) else Component.text("Click to select", NamedTextColor.YELLOW))
                .onClick { event ->
                    val player = event.whoClicked as Player
                    ImportWizardGUI(plugin, ImportStep.SELECT_SOURCE, source.id).open(player)
                }
                .build()
            )
            slot++
        }

        // Navigation
        setupStepNavigation(
            canGoBack = true,
            canGoNext = selectedSourceId != null,
            onBack = { player ->
                ImportWizardGUI(plugin, ImportStep.DETECT_SOURCES).open(player)
            },
            onNext = { player ->
                loadPreview(player)
            }
        )
    }

    private fun loadPreview(player: Player) {
        val sourceId = selectedSourceId ?: return

        plugin.scope.launch {
            val importService = plugin.serviceManager.importService
            val previewResult = importService.previewImport(sourceId)

            if (previewResult != null) {
                ImportWizardGUI(plugin, ImportStep.PREVIEW, sourceId, previewResult.mapping, previewResult).open(player)
            } else {
                player.sendMessage(Component.text("Failed to generate preview.", NamedTextColor.RED))
            }
        }
    }

    private fun setupPreview() {
        items.clear()
        fillBorder(GUIComponents.filler())

        val p = preview ?: return

        // Header
        setItem(0, 4, GUIItemBuilder()
            .material(Material.WRITABLE_BOOK)
            .name("Import Preview", NamedTextColor.AQUA)
            .lore(Component.text("Source: ${p.source}", NamedTextColor.WHITE))
            .build()
        )

        // Statistics
        setItem(2, 2, GUIItemBuilder()
            .material(Material.PLAYER_HEAD)
            .name("Players", NamedTextColor.YELLOW)
            .lore(Component.text("Total: ${p.totalPlayers}", NamedTextColor.GREEN))
            .lore(Component.text("To Skip: ${p.playersToSkip}", NamedTextColor.GRAY))
            .lore(Component.text("To Import: ${p.totalPlayers - p.playersToSkip}", NamedTextColor.WHITE))
            .build()
        )

        setItem(2, 4, GUIItemBuilder()
            .material(Material.CHEST)
            .name("Groups", NamedTextColor.YELLOW)
            .lore(Component.text("${p.groups.size} group(s)", NamedTextColor.WHITE))
            .apply {
                p.groups.take(5).forEach { group ->
                    lore(Component.text("  - ${group.name}", NamedTextColor.GRAY))
                }
            }
            .build()
        )

        setItem(2, 6, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Estimated Size", NamedTextColor.YELLOW)
            .lore(Component.text("~${formatBytes(p.estimatedDataSize)}", NamedTextColor.WHITE))
            .build()
        )

        // Warnings
        if (p.warnings.isNotEmpty()) {
            setItem(3, 4, GUIItemBuilder()
                .material(Material.YELLOW_BANNER)
                .name("Warnings", NamedTextColor.YELLOW)
                .apply {
                    p.warnings.take(5).forEach { warning ->
                        lore(Component.text("- $warning", NamedTextColor.YELLOW))
                    }
                }
                .build()
            )
        }

        // Navigation
        setupStepNavigation(
            canGoBack = true,
            canGoNext = true,
            onBack = { player ->
                ImportWizardGUI(plugin, ImportStep.SELECT_SOURCE, selectedSourceId).open(player)
            },
            onNext = { player ->
                ImportWizardGUI(plugin, ImportStep.CONFIGURE_MAPPING, selectedSourceId, p.mapping, p).open(player)
            }
        )
    }

    private fun setupConfigureMapping() {
        items.clear()
        fillBorder(GUIComponents.filler())

        val p = preview ?: return
        val m = mapping ?: p.mapping

        // Header
        setItem(0, 4, GUIItemBuilder()
            .material(Material.ANVIL)
            .name("Configure Group Mapping", NamedTextColor.AQUA)
            .lore("Map source groups to target groups")
            .build()
        )

        // Group mappings
        var slot = 19
        for ((sourceGroup, targetGroup) in m.groupMappings) {
            setItem(slot, GUIItemBuilder()
                .material(Material.MAP)
                .name(Component.text("$sourceGroup -> $targetGroup", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                .lore("Click to change target group")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    player.closeInventory()
                    player.sendMessage(Component.text("Enter the target group name for '$sourceGroup' (or 'cancel'):", NamedTextColor.YELLOW))
                    plugin.guiManager.registerChatInput(player) { input ->
                        if (input.lowercase() == "cancel") {
                            ImportWizardGUI(plugin, ImportStep.CONFIGURE_MAPPING, selectedSourceId, m, p).open(player)
                        } else {
                            val newMappings = m.groupMappings.toMutableMap()
                            newMappings[sourceGroup] = input
                            val newMapping = m.copy(groupMappings = newMappings)
                            ImportWizardGUI(plugin, ImportStep.CONFIGURE_MAPPING, selectedSourceId, newMapping, p).open(player)
                        }
                    }
                }
                .build()
            )
            slot++
        }

        // Options
        setItem(4, 2, GUIItemBuilder()
            .material(if (m.options.overwriteExisting) Material.LIME_DYE else Material.GRAY_DYE)
            .name("Overwrite Existing", if (m.options.overwriteExisting) NamedTextColor.GREEN else NamedTextColor.GRAY)
            .lore(if (m.options.overwriteExisting) "Will overwrite existing data" else "Will skip existing data")
            .lore("")
            .lore("Click to toggle")
            .onClick { event ->
                val player = event.whoClicked as Player
                val newOptions = m.options.copy(overwriteExisting = !m.options.overwriteExisting)
                val newMapping = m.copy(options = newOptions)
                ImportWizardGUI(plugin, ImportStep.CONFIGURE_MAPPING, selectedSourceId, newMapping, p).open(player)
            }
            .build()
        )

        setItem(4, 4, GUIItemBuilder()
            .material(if (m.options.createMissingGroups) Material.LIME_DYE else Material.GRAY_DYE)
            .name("Create Missing Groups", if (m.options.createMissingGroups) NamedTextColor.GREEN else NamedTextColor.GRAY)
            .lore(if (m.options.createMissingGroups) "Will create groups if needed" else "Will fail if groups missing")
            .lore("")
            .lore("Click to toggle")
            .onClick { event ->
                val player = event.whoClicked as Player
                val newOptions = m.options.copy(createMissingGroups = !m.options.createMissingGroups)
                val newMapping = m.copy(options = newOptions)
                ImportWizardGUI(plugin, ImportStep.CONFIGURE_MAPPING, selectedSourceId, newMapping, p).open(player)
            }
            .build()
        )

        setItem(4, 6, GUIItemBuilder()
            .material(if (m.options.importBalances) Material.GOLD_INGOT else Material.IRON_INGOT)
            .name("Import Balances", if (m.options.importBalances) NamedTextColor.GREEN else NamedTextColor.GRAY)
            .lore(if (m.options.importBalances) "Will import economy balances" else "Will skip economy balances")
            .lore("")
            .lore("Click to toggle")
            .onClick { event ->
                val player = event.whoClicked as Player
                val newOptions = m.options.copy(importBalances = !m.options.importBalances)
                val newMapping = m.copy(options = newOptions)
                ImportWizardGUI(plugin, ImportStep.CONFIGURE_MAPPING, selectedSourceId, newMapping, p).open(player)
            }
            .build()
        )

        // Navigation
        setupStepNavigation(
            canGoBack = true,
            canGoNext = true,
            nextText = "Start Import",
            nextMaterial = Material.EMERALD_BLOCK,
            onBack = { player ->
                ImportWizardGUI(plugin, ImportStep.PREVIEW, selectedSourceId, m, p).open(player)
            },
            onNext = { player ->
                startImport(player, m)
            }
        )
    }

    private fun startImport(player: Player, importMapping: ImportMapping) {
        ImportWizardGUI(plugin, ImportStep.IMPORTING, selectedSourceId, importMapping, preview).open(player)

        plugin.scope.launch {
            val importService = plugin.serviceManager.importService
            val sourceId = selectedSourceId ?: return@launch

            val importResult = importService.executeImport(sourceId, importMapping)
            ImportWizardGUI(plugin, ImportStep.RESULTS, selectedSourceId, importMapping, preview, importResult).open(player)
        }
    }

    private fun setupImporting() {
        items.clear()
        fillBorder(GUIComponents.filler())

        // Progress indicator
        setItem(2, 4, GUIItemBuilder()
            .material(Material.CLOCK)
            .name("Import in Progress", NamedTextColor.YELLOW)
            .lore("Please wait...")
            .lore("")
            .lore("Do not close this menu")
            .build()
        )

        // Animated progress (simple version)
        for (i in 0..6) {
            setItem(3, 1 + i, GUIItemBuilder()
                .material(Material.LIME_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build()
            )
        }
    }

    private fun setupResults() {
        items.clear()
        fillBorder(GUIComponents.filler())

        val r = result ?: return

        // Result header
        val headerMaterial = if (r.success) Material.EMERALD_BLOCK else Material.REDSTONE_BLOCK
        val headerColor = if (r.success) NamedTextColor.GREEN else NamedTextColor.RED
        val headerText = if (r.success) "Import Complete!" else "Import Failed"

        setItem(1, 4, GUIItemBuilder()
            .material(headerMaterial)
            .name(headerText, headerColor)
            .lore(Component.text("Duration: ${r.durationMs}ms", NamedTextColor.GRAY))
            .build()
        )

        // Statistics
        setItem(2, 2, GUIItemBuilder()
            .material(Material.EMERALD)
            .name("Imported", NamedTextColor.GREEN)
            .lore(Component.text("${r.playersImported} players", NamedTextColor.WHITE))
            .build()
        )

        setItem(2, 4, GUIItemBuilder()
            .material(Material.GOLD_INGOT)
            .name("Skipped", NamedTextColor.YELLOW)
            .lore(Component.text("${r.playersSkipped} players", NamedTextColor.WHITE))
            .build()
        )

        setItem(2, 6, GUIItemBuilder()
            .material(Material.REDSTONE)
            .name("Failed", NamedTextColor.RED)
            .lore(Component.text("${r.playersFailed} players", NamedTextColor.WHITE))
            .build()
        )

        // Errors (if any)
        if (r.errors.isNotEmpty()) {
            setItem(3, 4, GUIItemBuilder()
                .material(Material.RED_BANNER)
                .name("Errors", NamedTextColor.RED)
                .apply {
                    r.errors.take(5).forEach { error ->
                        lore(Component.text("- ${error.message.take(40)}", NamedTextColor.RED))
                    }
                    if (r.errors.size > 5) {
                        lore(Component.text("... and ${r.errors.size - 5} more", NamedTextColor.DARK_RED))
                    }
                }
                .build()
            )
        }

        // Finish button
        setItem(5, 4, GUIItemBuilder()
            .material(Material.BARRIER)
            .name("Close", NamedTextColor.RED)
            .lore("Click to close the wizard")
            .onClick { event ->
                event.whoClicked.closeInventory()
            }
            .build()
        )
    }

    private fun setupStepNavigation(
        canGoBack: Boolean,
        canGoNext: Boolean,
        nextText: String = "Next",
        nextMaterial: Material = Material.ARROW,
        onBack: ((Player) -> Unit)? = null,
        onNext: ((Player) -> Unit)? = null
    ) {
        // Back button
        if (canGoBack && onBack != null) {
            setItem(5, 2, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Back", NamedTextColor.GRAY)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    onBack(player)
                }
                .build()
            )
        }

        // Next button
        if (canGoNext && onNext != null) {
            setItem(5, 6, GUIItemBuilder()
                .material(nextMaterial)
                .name(nextText, NamedTextColor.GREEN)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    onNext(player)
                }
                .build()
            )
        }

        // Close button
        setItem(5, 8, GUIComponents.closeButton())
    }

    private fun getSourceMaterial(sourceId: String): Material {
        return when {
            sourceId.contains("pwi", ignoreCase = true) -> Material.ENDER_CHEST
            sourceId.contains("mvi", ignoreCase = true) -> Material.CHEST
            sourceId.contains("myworlds", ignoreCase = true) -> Material.GRASS_BLOCK
            else -> Material.PAPER
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    override fun fillEmptySlots(inventory: Inventory) {
        val filler = GUIComponents.filler()
        for (i in 0 until size) {
            if (!items.containsKey(i)) {
                items[i] = filler
                inventory.setItem(i, filler.itemStack)
            }
        }
    }
}
