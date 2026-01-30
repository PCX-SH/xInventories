package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.model.AuditAction
import sh.pcx.xinventories.internal.model.AuditEntry
import java.time.Instant
import java.util.UUID

@DisplayName("AuditEntry")
class AuditEntryTest {

    private val testUuid = UUID.randomUUID()
    private val testActorUuid = UUID.randomUUID()

    @Nested
    @DisplayName("Factory Methods")
    inner class FactoryMethods {

        @Test
        @DisplayName("system() creates entry with null actor and SYSTEM name")
        fun systemCreatesEntryWithNullActorAndSystemName() {
            val entry = AuditEntry.system(
                target = testUuid,
                targetName = "TestPlayer",
                action = AuditAction.INVENTORY_SAVE,
                group = "survival"
            )

            assertNull(entry.actor)
            assertEquals("SYSTEM", entry.actorName)
            assertEquals(testUuid, entry.target)
            assertEquals("TestPlayer", entry.targetName)
            assertEquals(AuditAction.INVENTORY_SAVE, entry.action)
            assertEquals("survival", entry.group)
        }

        @Test
        @DisplayName("console() creates entry with null actor and CONSOLE name")
        fun consoleCreatesEntryWithNullActorAndConsoleName() {
            val entry = AuditEntry.console(
                target = testUuid,
                targetName = "TestPlayer",
                action = AuditAction.INVENTORY_CLEAR,
                group = "creative"
            )

            assertNull(entry.actor)
            assertEquals("CONSOLE", entry.actorName)
            assertEquals(testUuid, entry.target)
            assertEquals("TestPlayer", entry.targetName)
            assertEquals(AuditAction.INVENTORY_CLEAR, entry.action)
            assertEquals("creative", entry.group)
        }

        @Test
        @DisplayName("player() creates entry with actor UUID and name")
        fun playerCreatesEntryWithActorUuidAndName() {
            val entry = AuditEntry.player(
                actor = testActorUuid,
                actorName = "AdminPlayer",
                target = testUuid,
                targetName = "TestPlayer",
                action = AuditAction.ADMIN_EDIT,
                group = "survival",
                details = "Modified armor"
            )

            assertEquals(testActorUuid, entry.actor)
            assertEquals("AdminPlayer", entry.actorName)
            assertEquals(testUuid, entry.target)
            assertEquals("TestPlayer", entry.targetName)
            assertEquals(AuditAction.ADMIN_EDIT, entry.action)
            assertEquals("survival", entry.group)
            assertEquals("Modified armor", entry.details)
        }
    }

    @Nested
    @DisplayName("Properties")
    inner class Properties {

        @Test
        @DisplayName("default id is 0")
        fun defaultIdIsZero() {
            val entry = AuditEntry.system(testUuid, "Test", AuditAction.INVENTORY_SAVE)
            assertEquals(0, entry.id)
        }

        @Test
        @DisplayName("default timestamp is now")
        fun defaultTimestampIsNow() {
            val before = Instant.now()
            val entry = AuditEntry.system(testUuid, "Test", AuditAction.INVENTORY_SAVE)
            val after = Instant.now()

            assertTrue(entry.timestamp >= before)
            assertTrue(entry.timestamp <= after)
        }

        @Test
        @DisplayName("group can be null")
        fun groupCanBeNull() {
            val entry = AuditEntry.system(testUuid, "Test", AuditAction.LOCK_APPLY)
            assertNull(entry.group)
        }

        @Test
        @DisplayName("details can be null")
        fun detailsCanBeNull() {
            val entry = AuditEntry.system(testUuid, "Test", AuditAction.INVENTORY_SAVE, group = "survival")
            assertNull(entry.details)
        }

        @Test
        @DisplayName("serverId can be null")
        fun serverIdCanBeNull() {
            val entry = AuditEntry.system(testUuid, "Test", AuditAction.INVENTORY_SAVE)
            assertNull(entry.serverId)
        }

        @Test
        @DisplayName("serverId can be set")
        fun serverIdCanBeSet() {
            val entry = AuditEntry.system(testUuid, "Test", AuditAction.INVENTORY_SAVE, serverId = "server-1")
            assertEquals("server-1", entry.serverId)
        }
    }

    @Nested
    @DisplayName("toDisplayString")
    inner class ToDisplayString {

        @Test
        @DisplayName("formats system action correctly")
        fun formatsSystemActionCorrectly() {
            val entry = AuditEntry.system(
                target = testUuid,
                targetName = "TestPlayer",
                action = AuditAction.INVENTORY_SAVE,
                group = "survival"
            )

            val display = entry.toDisplayString()

            assertTrue(display.contains("[SYSTEM]"))
            assertTrue(display.contains("saved inventory for"))
            assertTrue(display.contains("TestPlayer"))
            assertTrue(display.contains("survival"))
        }

        @Test
        @DisplayName("formats player action correctly")
        fun formatsPlayerActionCorrectly() {
            val entry = AuditEntry.player(
                actor = testActorUuid,
                actorName = "AdminPlayer",
                target = testUuid,
                targetName = "TestPlayer",
                action = AuditAction.ADMIN_EDIT
            )

            val display = entry.toDisplayString()

            assertTrue(display.contains("AdminPlayer"))
            assertTrue(display.contains("edited inventory for"))
            assertTrue(display.contains("TestPlayer"))
        }

        @Test
        @DisplayName("includes details when present")
        fun includesDetailsWhenPresent() {
            val entry = AuditEntry.system(
                target = testUuid,
                targetName = "TestPlayer",
                action = AuditAction.VERSION_RESTORE,
                details = "Restored version #5"
            )

            val display = entry.toDisplayString()

            assertTrue(display.contains("Restored version #5"))
        }
    }

    @Nested
    @DisplayName("AuditAction")
    inner class AuditActionTests {

        @Test
        @DisplayName("fromName returns correct action for valid name")
        fun fromNameReturnsCorrectActionForValidName() {
            assertEquals(AuditAction.INVENTORY_SAVE, AuditAction.fromName("INVENTORY_SAVE"))
            assertEquals(AuditAction.ADMIN_EDIT, AuditAction.fromName("admin_edit"))
            assertEquals(AuditAction.BULK_CLEAR, AuditAction.fromName("Bulk_Clear"))
        }

        @Test
        @DisplayName("fromName returns null for invalid name")
        fun fromNameReturnsNullForInvalidName() {
            assertNull(AuditAction.fromName("INVALID_ACTION"))
            assertNull(AuditAction.fromName(""))
            assertNull(AuditAction.fromName("xyz"))
        }

        @Test
        @DisplayName("destructive actions are marked correctly")
        fun destructiveActionsAreMarkedCorrectly() {
            assertTrue(AuditAction.INVENTORY_CLEAR.isDestructive)
            assertTrue(AuditAction.ITEM_REMOVE.isDestructive)
            assertTrue(AuditAction.BULK_OPERATION.isDestructive)
            assertTrue(AuditAction.BULK_CLEAR.isDestructive)
            assertTrue(AuditAction.BULK_RESET_STATS.isDestructive)

            assertFalse(AuditAction.INVENTORY_SAVE.isDestructive)
            assertFalse(AuditAction.INVENTORY_LOAD.isDestructive)
            assertFalse(AuditAction.ADMIN_VIEW.isDestructive)
            assertFalse(AuditAction.TEMPLATE_APPLY.isDestructive)
        }

        @Test
        @DisplayName("all actions have display names")
        fun allActionsHaveDisplayNames() {
            for (action in AuditAction.entries) {
                assertNotNull(action.displayName)
                assertTrue(action.displayName.isNotBlank())
            }
        }
    }

    @Nested
    @DisplayName("Data Class Features")
    inner class DataClassFeatures {

        @Test
        @DisplayName("copy creates new instance with modified properties")
        fun copyCreatesNewInstanceWithModifiedProperties() {
            val original = AuditEntry.system(testUuid, "Test", AuditAction.INVENTORY_SAVE, "survival")
            val copy = original.copy(details = "Added detail")

            assertEquals(original.target, copy.target)
            assertEquals(original.targetName, copy.targetName)
            assertEquals(original.action, copy.action)
            assertEquals(original.group, copy.group)
            assertNull(original.details)
            assertEquals("Added detail", copy.details)
        }

        @Test
        @DisplayName("equals works correctly")
        fun equalsWorksCorrectly() {
            val timestamp = Instant.now()
            val entry1 = AuditEntry(
                id = 1,
                timestamp = timestamp,
                actor = null,
                actorName = "SYSTEM",
                target = testUuid,
                targetName = "Test",
                action = AuditAction.INVENTORY_SAVE,
                group = "survival"
            )
            val entry2 = AuditEntry(
                id = 1,
                timestamp = timestamp,
                actor = null,
                actorName = "SYSTEM",
                target = testUuid,
                targetName = "Test",
                action = AuditAction.INVENTORY_SAVE,
                group = "survival"
            )

            assertEquals(entry1, entry2)
        }
    }
}
