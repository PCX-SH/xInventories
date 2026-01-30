package sh.pcx.xinventories.unit.sync

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import sh.pcx.xinventories.internal.model.SyncMessage
import sh.pcx.xinventories.internal.sync.MessageSerializer
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for MessageSerializer.
 * Tests JSON serialization and deserialization of all SyncMessage types.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageSerializerTest {

    private val testUuid = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    private val testServerId = "server-1"
    private val testTimestamp = 1700000000000L

    @BeforeAll
    fun setUp() {
        Logging.init(Logger.getLogger("MessageSerializerTest"), debug = true)
    }

    @Nested
    @DisplayName("AcquireLock Message")
    inner class AcquireLockTests {

        @Test
        @DisplayName("should serialize and deserialize AcquireLock")
        fun testAcquireLockRoundTrip() {
            val message = SyncMessage.acquireLock(testUuid, testServerId)

            val json = MessageSerializer.serialize(message)
            val deserialized = MessageSerializer.deserialize(json)

            Assertions.assertNotNull(deserialized)
            assertTrue(deserialized is SyncMessage.AcquireLock)
            val acquireLock = deserialized as SyncMessage.AcquireLock
            assertEquals(testUuid.toString(), acquireLock.playerUuid)
            assertEquals(testServerId, acquireLock.serverId)
        }

        @Test
        @DisplayName("should include type discriminator in JSON")
        fun testAcquireLockJsonContainsType() {
            val message = SyncMessage.AcquireLock(
                playerUuid = testUuid.toString(),
                serverId = testServerId,
                timestamp = testTimestamp
            )

            val json = MessageSerializer.serialize(message)

            assertTrue(json.contains("acquire_lock"))
        }
    }

    @Nested
    @DisplayName("ReleaseLock Message")
    inner class ReleaseLockTests {

        @Test
        @DisplayName("should serialize and deserialize ReleaseLock")
        fun testReleaseLockRoundTrip() {
            val message = SyncMessage.releaseLock(testUuid, testServerId)

            val json = MessageSerializer.serialize(message)
            val deserialized = MessageSerializer.deserialize(json)

            Assertions.assertNotNull(deserialized)
            assertTrue(deserialized is SyncMessage.ReleaseLock)
            val releaseLock = deserialized as SyncMessage.ReleaseLock
            assertEquals(testUuid.toString(), releaseLock.playerUuid)
            assertEquals(testServerId, releaseLock.serverId)
        }
    }

    @Nested
    @DisplayName("TransferLock Message")
    inner class TransferLockTests {

        @Test
        @DisplayName("should serialize and deserialize TransferLock")
        fun testTransferLockRoundTrip() {
            val message = SyncMessage.transferLock(testUuid, "server-1", "server-2")

            val json = MessageSerializer.serialize(message)
            val deserialized = MessageSerializer.deserialize(json)

            Assertions.assertNotNull(deserialized)
            assertTrue(deserialized is SyncMessage.TransferLock)
            val transferLock = deserialized as SyncMessage.TransferLock
            assertEquals(testUuid.toString(), transferLock.playerUuid)
            assertEquals("server-1", transferLock.fromServer)
            assertEquals("server-2", transferLock.toServer)
        }
    }

    @Nested
    @DisplayName("DataUpdate Message")
    inner class DataUpdateTests {

        @Test
        @DisplayName("should serialize and deserialize DataUpdate")
        fun testDataUpdateRoundTrip() {
            val message = SyncMessage.dataUpdate(testUuid, "survival", 42L, testServerId)

            val json = MessageSerializer.serialize(message)
            val deserialized = MessageSerializer.deserialize(json)

            Assertions.assertNotNull(deserialized)
            assertTrue(deserialized is SyncMessage.DataUpdate)
            val dataUpdate = deserialized as SyncMessage.DataUpdate
            assertEquals(testUuid.toString(), dataUpdate.playerUuid)
            assertEquals("survival", dataUpdate.group)
            assertEquals(42L, dataUpdate.version)
            assertEquals(testServerId, dataUpdate.serverId)
        }
    }

    @Nested
    @DisplayName("CacheInvalidate Message")
    inner class CacheInvalidateTests {

        @Test
        @DisplayName("should serialize and deserialize CacheInvalidate with group")
        fun testCacheInvalidateWithGroupRoundTrip() {
            val message = SyncMessage.cacheInvalidate(testUuid, "survival")

            val json = MessageSerializer.serialize(message)
            val deserialized = MessageSerializer.deserialize(json)

            Assertions.assertNotNull(deserialized)
            assertTrue(deserialized is SyncMessage.CacheInvalidate)
            val cacheInvalidate = deserialized as SyncMessage.CacheInvalidate
            assertEquals(testUuid.toString(), cacheInvalidate.playerUuid)
            assertEquals("survival", cacheInvalidate.group)
        }

        @Test
        @DisplayName("should serialize and deserialize CacheInvalidate without group")
        fun testCacheInvalidateWithoutGroupRoundTrip() {
            val message = SyncMessage.cacheInvalidate(testUuid)

            val json = MessageSerializer.serialize(message)
            val deserialized = MessageSerializer.deserialize(json)

            Assertions.assertNotNull(deserialized)
            assertTrue(deserialized is SyncMessage.CacheInvalidate)
            val cacheInvalidate = deserialized as SyncMessage.CacheInvalidate
            assertEquals(testUuid.toString(), cacheInvalidate.playerUuid)
            Assertions.assertNull(cacheInvalidate.group)
        }
    }

    @Nested
    @DisplayName("Heartbeat Message")
    inner class HeartbeatTests {

        @Test
        @DisplayName("should serialize and deserialize Heartbeat")
        fun testHeartbeatRoundTrip() {
            val message = SyncMessage.heartbeat(testServerId, 50)

            val json = MessageSerializer.serialize(message)
            val deserialized = MessageSerializer.deserialize(json)

            Assertions.assertNotNull(deserialized)
            assertTrue(deserialized is SyncMessage.Heartbeat)
            val heartbeat = deserialized as SyncMessage.Heartbeat
            assertEquals(testServerId, heartbeat.serverId)
            assertEquals(50, heartbeat.playerCount)
        }
    }

    @Nested
    @DisplayName("LockAck Message")
    inner class LockAckTests {

        @Test
        @DisplayName("should serialize and deserialize LockAck when granted")
        fun testLockAckGrantedRoundTrip() {
            val message = SyncMessage.LockAck(
                playerUuid = testUuid.toString(),
                serverId = testServerId,
                granted = true,
                currentHolder = null
            )

            val json = MessageSerializer.serialize(message)
            val deserialized = MessageSerializer.deserialize(json)

            Assertions.assertNotNull(deserialized)
            assertTrue(deserialized is SyncMessage.LockAck)
            val lockAck = deserialized as SyncMessage.LockAck
            assertTrue(lockAck.granted)
            Assertions.assertNull(lockAck.currentHolder)
        }

        @Test
        @DisplayName("should serialize and deserialize LockAck when denied")
        fun testLockAckDeniedRoundTrip() {
            val message = SyncMessage.LockAck(
                playerUuid = testUuid.toString(),
                serverId = testServerId,
                granted = false,
                currentHolder = "server-2"
            )

            val json = MessageSerializer.serialize(message)
            val deserialized = MessageSerializer.deserialize(json)

            Assertions.assertNotNull(deserialized)
            assertTrue(deserialized is SyncMessage.LockAck)
            val lockAck = deserialized as SyncMessage.LockAck
            assertFalse(lockAck.granted)
            assertEquals("server-2", lockAck.currentHolder)
        }
    }

    @Nested
    @DisplayName("ServerShutdown Message")
    inner class ServerShutdownTests {

        @Test
        @DisplayName("should serialize and deserialize ServerShutdown")
        fun testServerShutdownRoundTrip() {
            val message = SyncMessage.serverShutdown(testServerId)

            val json = MessageSerializer.serialize(message)
            val deserialized = MessageSerializer.deserialize(json)

            Assertions.assertNotNull(deserialized)
            assertTrue(deserialized is SyncMessage.ServerShutdown)
            val serverShutdown = deserialized as SyncMessage.ServerShutdown
            assertEquals(testServerId, serverShutdown.serverId)
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("should return null for invalid JSON")
        fun testInvalidJson() {
            val result = MessageSerializer.deserialize("not valid json")

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("should return null for empty string")
        fun testEmptyString() {
            val result = MessageSerializer.deserialize("")

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("should return null for JSON without type discriminator")
        fun testMissingType() {
            val result = MessageSerializer.deserialize("""{"playerUuid": "test"}""")

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("should return null for unknown message type")
        fun testUnknownType() {
            val result = MessageSerializer.deserialize("""{"type": "unknown_type"}""")

            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("UUID Conversion")
    inner class UuidConversionTests {

        @Test
        @DisplayName("should convert playerUuid string to UUID")
        fun testUuidConversion() {
            val message = SyncMessage.AcquireLock(
                playerUuid = testUuid.toString(),
                serverId = testServerId,
                timestamp = testTimestamp
            )

            assertEquals(testUuid, message.getPlayerUUID())
        }

        @Test
        @DisplayName("should preserve UUID through serialization")
        fun testUuidPreservedThroughSerialization() {
            val originalUuid = UUID.randomUUID()
            val message = SyncMessage.acquireLock(originalUuid, testServerId)

            val json = MessageSerializer.serialize(message)
            val deserialized = MessageSerializer.deserializeAs<SyncMessage.AcquireLock>(json)

            Assertions.assertNotNull(deserialized)
            assertEquals(originalUuid, deserialized!!.getPlayerUUID())
        }
    }

    @Nested
    @DisplayName("Validation")
    inner class ValidationTests {

        @Test
        @DisplayName("should validate correct JSON")
        fun testIsValidWithCorrectJson() {
            val message = SyncMessage.heartbeat(testServerId, 0)
            val json = MessageSerializer.serialize(message)

            assertTrue(MessageSerializer.isValid(json))
        }

        @Test
        @DisplayName("should not validate invalid JSON")
        fun testIsValidWithInvalidJson() {
            assertFalse(MessageSerializer.isValid("not json"))
        }
    }
}
