package sh.pcx.xinventories.internal.api

import io.mockk.*
import org.bukkit.entity.Player
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.config.SharedSlotsConfigSection
import sh.pcx.xinventories.internal.model.SharedSlotEntry
import sh.pcx.xinventories.internal.model.SharedSlotsConfig
import sh.pcx.xinventories.internal.model.SlotMode
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.service.SharedSlotService
import java.util.*

@DisplayName("SharedSlotsAPIImpl")
class SharedSlotsAPIImplTest {

    private lateinit var plugin: XInventories
    private lateinit var api: SharedSlotsAPIImpl
    private lateinit var serviceManager: ServiceManager
    private lateinit var sharedSlotService: SharedSlotService
    private lateinit var configManager: ConfigManager

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        sharedSlotService = mockk(relaxed = true)
        configManager = mockk(relaxed = true)

        every { plugin.serviceManager } returns serviceManager
        every { plugin.configManager } returns configManager
        every { serviceManager.sharedSlotService } returns sharedSlotService

        val mainConfig = mockk<MainConfig>()
        val sharedSlotsConfig = mockk<SharedSlotsConfigSection>()
        every { configManager.mainConfig } returns mainConfig
        every { mainConfig.sharedSlots } returns sharedSlotsConfig
        every { sharedSlotsConfig.toSharedSlotsConfig() } returns SharedSlotsConfig(enabled = true, slots = emptyList())

        api = SharedSlotsAPIImpl(plugin)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    @DisplayName("getConfig")
    inner class GetConfig {

        @Test
        @DisplayName("returns current config")
        fun returnsCurrentConfig() {
            val result = api.getConfig()

            Assertions.assertNotNull(result)
            assertTrue(result.enabled)
        }
    }

    @Nested
    @DisplayName("setConfig")
    inner class SetConfig {

        @Test
        @DisplayName("updates config and notifies service")
        fun updatesConfigAndNotifiesService() {
            val newConfig = SharedSlotsConfig(enabled = false, slots = emptyList())
            every { sharedSlotService.updateConfig(any()) } just Runs

            api.setConfig(newConfig)

            verify { sharedSlotService.updateConfig(newConfig) }
            assertFalse(api.getConfig().enabled)
        }
    }

    @Nested
    @DisplayName("isEnabled")
    inner class IsEnabled {

        @Test
        @DisplayName("returns true when enabled")
        fun returnsTrueWhenEnabled() {
            val result = api.isEnabled()

            assertTrue(result)
        }
    }

    @Nested
    @DisplayName("setEnabled")
    inner class SetEnabled {

        @Test
        @DisplayName("enables shared slots")
        fun enablesSharedSlots() {
            every { sharedSlotService.updateConfig(any()) } just Runs

            api.setEnabled(true)

            assertTrue(api.isEnabled())
            verify { sharedSlotService.updateConfig(match { it.enabled }) }
        }

        @Test
        @DisplayName("disables shared slots")
        fun disablesSharedSlots() {
            every { sharedSlotService.updateConfig(any()) } just Runs

            api.setEnabled(false)

            assertFalse(api.isEnabled())
            verify { sharedSlotService.updateConfig(match { !it.enabled }) }
        }
    }

    @Nested
    @DisplayName("isSharedSlot")
    inner class IsSharedSlot {

        @Test
        @DisplayName("delegates to service")
        fun delegatesToService() {
            every { sharedSlotService.isSharedSlot(8) } returns true
            every { sharedSlotService.isSharedSlot(0) } returns false

            assertTrue(api.isSharedSlot(8))
            assertFalse(api.isSharedSlot(0))
        }
    }

    @Nested
    @DisplayName("isLockedSlot")
    inner class IsLockedSlot {

        @Test
        @DisplayName("delegates to service")
        fun delegatesToService() {
            every { sharedSlotService.isLockedSlot(8) } returns true
            every { sharedSlotService.isLockedSlot(0) } returns false

            assertTrue(api.isLockedSlot(8))
            assertFalse(api.isLockedSlot(0))
        }
    }

    @Nested
    @DisplayName("isSyncedSlot")
    inner class IsSyncedSlot {

        @Test
        @DisplayName("returns true for synced slot")
        fun returnsTrueForSyncedSlot() {
            val entry = mockk<SharedSlotEntry>()
            every { entry.mode } returns SlotMode.SYNC
            every { sharedSlotService.getEntryForSlot(8) } returns entry

            val result = api.isSyncedSlot(8)

            assertTrue(result)
        }

        @Test
        @DisplayName("returns false for non-synced slot")
        fun returnsFalseForNonSyncedSlot() {
            val entry = mockk<SharedSlotEntry>()
            every { entry.mode } returns SlotMode.PRESERVE
            every { sharedSlotService.getEntryForSlot(8) } returns entry

            val result = api.isSyncedSlot(8)

            assertFalse(result)
        }

        @Test
        @DisplayName("returns false for non-shared slot")
        fun returnsFalseForNonSharedSlot() {
            every { sharedSlotService.getEntryForSlot(0) } returns null

            val result = api.isSyncedSlot(0)

            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("getSlotEntry")
    inner class GetSlotEntry {

        @Test
        @DisplayName("returns entry for shared slot")
        fun returnsEntryForSharedSlot() {
            val entry = mockk<SharedSlotEntry>()
            every { sharedSlotService.getEntryForSlot(8) } returns entry

            val result = api.getSlotEntry(8)

            assertEquals(entry, result)
        }

        @Test
        @DisplayName("returns null for non-shared slot")
        fun returnsNullForNonSharedSlot() {
            every { sharedSlotService.getEntryForSlot(0) } returns null

            val result = api.getSlotEntry(0)

            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("getAllSharedSlots")
    inner class GetAllSharedSlots {

        @Test
        @DisplayName("returns all shared slot indices")
        fun returnsAllSharedSlotIndices() {
            val mainConfig = mockk<MainConfig>()
            val sharedSlotsConfig = mockk<SharedSlotsConfigSection>()
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(
                    SharedSlotEntry(slot = 8),
                    SharedSlotEntry(slot = 17)
                )
            )
            every { configManager.mainConfig } returns mainConfig
            every { mainConfig.sharedSlots } returns sharedSlotsConfig
            every { sharedSlotsConfig.toSharedSlotsConfig() } returns config

            // Recreate API with new config
            val newApi = SharedSlotsAPIImpl(plugin)
            val result = newApi.getAllSharedSlots()

            assertEquals(2, result.size)
            assertTrue(result.contains(8))
            assertTrue(result.contains(17))
        }
    }

    @Nested
    @DisplayName("preserveSlots")
    inner class PreserveSlots {

        @Test
        @DisplayName("preserves shared slots for player")
        fun preservesSharedSlotsForPlayer() {
            val player = mockk<Player>()
            every { sharedSlotService.preserveSharedSlots(player) } just Runs

            api.preserveSlots(player)

            verify { sharedSlotService.preserveSharedSlots(player) }
        }
    }

    @Nested
    @DisplayName("restoreSlots")
    inner class RestoreSlots {

        @Test
        @DisplayName("restores shared slots for player")
        fun restoresSharedSlotsForPlayer() {
            val player = mockk<Player>()
            every { sharedSlotService.restoreSharedSlots(player) } just Runs

            api.restoreSlots(player)

            verify { sharedSlotService.restoreSharedSlots(player) }
        }
    }

    @Nested
    @DisplayName("clearPlayerCache")
    inner class ClearPlayerCache {

        @Test
        @DisplayName("clears cache for player")
        fun clearsCacheForPlayer() {
            val uuid = UUID.randomUUID()
            every { sharedSlotService.cleanup(uuid) } just Runs

            api.clearPlayerCache(uuid)

            verify { sharedSlotService.cleanup(uuid) }
        }
    }

    @Nested
    @DisplayName("applyEnforcedItems")
    inner class ApplyEnforcedItems {

        @Test
        @DisplayName("applies enforced items to player")
        fun appliesEnforcedItemsToPlayer() {
            val player = mockk<Player>()
            every { sharedSlotService.applyEnforcedItems(player) } just Runs

            api.applyEnforcedItems(player)

            verify { sharedSlotService.applyEnforcedItems(player) }
        }
    }
}
