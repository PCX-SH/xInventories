package sh.pcx.xinventories.unit.sync

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.model.SyncMessage
import java.util.UUID

/**
 * Unit tests for SyncMessage data classes.
 */
class SyncMessageTest {

    private val testUuid = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    private val testServerId = "server-1"

    @Nested
    @DisplayName("Factory Methods")
    inner class FactoryMethodTests {

        @Test
        @DisplayName("acquireLock should create message with current timestamp")
        fun testAcquireLockTimestamp() {
            val before = System.currentTimeMillis()
            val message = SyncMessage.acquireLock(testUuid, testServerId)
            val after = System.currentTimeMillis()

            assertTrue(message.timestamp >= before)
            assertTrue(message.timestamp <= after)
        }

        @Test
        @DisplayName("heartbeat should create message with current timestamp")
        fun testHeartbeatTimestamp() {
            val before = System.currentTimeMillis()
            val message = SyncMessage.heartbeat(testServerId, 10)
            val after = System.currentTimeMillis()

            assertTrue(message.timestamp >= before)
            assertTrue(message.timestamp <= after)
        }

        @Test
        @DisplayName("serverShutdown should create message with current timestamp")
        fun testServerShutdownTimestamp() {
            val before = System.currentTimeMillis()
            val message = SyncMessage.serverShutdown(testServerId)
            val after = System.currentTimeMillis()

            assertTrue(message.timestamp >= before)
            assertTrue(message.timestamp <= after)
        }
    }

    @Nested
    @DisplayName("UUID Conversion")
    inner class UuidConversionTests {

        @Test
        @DisplayName("AcquireLock getPlayerUUID should return correct UUID")
        fun testAcquireLockUuid() {
            val message = SyncMessage.acquireLock(testUuid, testServerId)
            assertEquals(testUuid, message.getPlayerUUID())
        }

        @Test
        @DisplayName("ReleaseLock getPlayerUUID should return correct UUID")
        fun testReleaseLockUuid() {
            val message = SyncMessage.releaseLock(testUuid, testServerId)
            assertEquals(testUuid, message.getPlayerUUID())
        }

        @Test
        @DisplayName("TransferLock getPlayerUUID should return correct UUID")
        fun testTransferLockUuid() {
            val message = SyncMessage.transferLock(testUuid, "server-1", "server-2")
            assertEquals(testUuid, message.getPlayerUUID())
        }

        @Test
        @DisplayName("DataUpdate getPlayerUUID should return correct UUID")
        fun testDataUpdateUuid() {
            val message = SyncMessage.dataUpdate(testUuid, "survival", 1L, testServerId)
            assertEquals(testUuid, message.getPlayerUUID())
        }

        @Test
        @DisplayName("CacheInvalidate getPlayerUUID should return correct UUID")
        fun testCacheInvalidateUuid() {
            val message = SyncMessage.cacheInvalidate(testUuid, "survival")
            assertEquals(testUuid, message.getPlayerUUID())
        }

        @Test
        @DisplayName("LockAck getPlayerUUID should return correct UUID")
        fun testLockAckUuid() {
            val message = SyncMessage.LockAck(
                playerUuid = testUuid.toString(),
                serverId = testServerId,
                granted = true
            )
            assertEquals(testUuid, message.getPlayerUUID())
        }
    }

    @Nested
    @DisplayName("Message Content")
    inner class MessageContentTests {

        @Test
        @DisplayName("DataUpdate should contain all required fields")
        fun testDataUpdateContent() {
            val message = SyncMessage.dataUpdate(testUuid, "creative", 42L, testServerId)

            assertEquals(testUuid.toString(), message.playerUuid)
            assertEquals("creative", message.group)
            assertEquals(42L, message.version)
            assertEquals(testServerId, message.serverId)
        }

        @Test
        @DisplayName("TransferLock should contain from and to servers")
        fun testTransferLockContent() {
            val message = SyncMessage.transferLock(testUuid, "server-1", "server-2")

            assertEquals("server-1", message.fromServer)
            assertEquals("server-2", message.toServer)
        }

        @Test
        @DisplayName("Heartbeat should contain player count")
        fun testHeartbeatContent() {
            val message = SyncMessage.heartbeat(testServerId, 100)

            assertEquals(testServerId, message.serverId)
            assertEquals(100, message.playerCount)
        }

        @Test
        @DisplayName("CacheInvalidate should have optional group")
        fun testCacheInvalidateOptionalGroup() {
            val withGroup = SyncMessage.cacheInvalidate(testUuid, "survival")
            val withoutGroup = SyncMessage.cacheInvalidate(testUuid)

            assertEquals("survival", withGroup.group)
            Assertions.assertNull(withoutGroup.group)
        }

        @Test
        @DisplayName("LockAck should have optional currentHolder")
        fun testLockAckOptionalHolder() {
            val granted = SyncMessage.LockAck(
                playerUuid = testUuid.toString(),
                serverId = testServerId,
                granted = true,
                currentHolder = null
            )
            val denied = SyncMessage.LockAck(
                playerUuid = testUuid.toString(),
                serverId = testServerId,
                granted = false,
                currentHolder = "server-2"
            )

            assertTrue(granted.granted)
            Assertions.assertNull(granted.currentHolder)
            assertFalse(denied.granted)
            assertEquals("server-2", denied.currentHolder)
        }
    }

    @Nested
    @DisplayName("Equality")
    inner class EqualityTests {

        @Test
        @DisplayName("identical messages should be equal")
        fun testMessageEquality() {
            val msg1 = SyncMessage.AcquireLock(testUuid.toString(), testServerId, 12345L)
            val msg2 = SyncMessage.AcquireLock(testUuid.toString(), testServerId, 12345L)

            assertEquals(msg1, msg2)
        }

        @Test
        @DisplayName("messages with different timestamps should not be equal")
        fun testMessageInequality() {
            val msg1 = SyncMessage.AcquireLock(testUuid.toString(), testServerId, 12345L)
            val msg2 = SyncMessage.AcquireLock(testUuid.toString(), testServerId, 12346L)

            assertNotEquals(msg1, msg2)
        }
    }
}
