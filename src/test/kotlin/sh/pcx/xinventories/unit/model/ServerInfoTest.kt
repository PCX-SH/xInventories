package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.model.ServerInfo
import java.time.Instant

/**
 * Unit tests for ServerInfo data class.
 */
class ServerInfoTest {

    @Nested
    @DisplayName("fromHeartbeat Factory")
    inner class FromHeartbeatTests {

        @Test
        @DisplayName("should create healthy server when heartbeat is recent")
        fun testHealthyServerFromRecentHeartbeat() {
            val now = System.currentTimeMillis()
            val serverInfo = ServerInfo.fromHeartbeat(
                serverId = "server-1",
                timestamp = now,
                playerCount = 50,
                timeoutSeconds = 90
            )

            assertEquals("server-1", serverInfo.serverId)
            assertEquals(50, serverInfo.playerCount)
            assertTrue(serverInfo.isHealthy)
        }

        @Test
        @DisplayName("should create unhealthy server when heartbeat is old")
        fun testUnhealthyServerFromOldHeartbeat() {
            val oldTimestamp = System.currentTimeMillis() - 120_000 // 2 minutes ago
            val serverInfo = ServerInfo.fromHeartbeat(
                serverId = "server-1",
                timestamp = oldTimestamp,
                playerCount = 25,
                timeoutSeconds = 90
            )

            assertEquals("server-1", serverInfo.serverId)
            assertEquals(25, serverInfo.playerCount)
            assertFalse(serverInfo.isHealthy)
        }

        @Test
        @DisplayName("should correctly set lastHeartbeat instant")
        fun testLastHeartbeatTimestamp() {
            val timestamp = 1700000000000L
            val serverInfo = ServerInfo.fromHeartbeat(
                serverId = "server-1",
                timestamp = timestamp,
                playerCount = 10,
                timeoutSeconds = 90
            )

            assertEquals(Instant.ofEpochMilli(timestamp), serverInfo.lastHeartbeat)
        }
    }

    @Nested
    @DisplayName("secondsSinceHeartbeat")
    inner class SecondsSinceHeartbeatTests {

        @Test
        @DisplayName("should return small value for recent heartbeat")
        fun testRecentHeartbeat() {
            val serverInfo = ServerInfo(
                serverId = "server-1",
                lastHeartbeat = Instant.now(),
                playerCount = 10,
                isHealthy = true
            )

            val seconds = serverInfo.secondsSinceHeartbeat()

            assertTrue(seconds >= 0)
            assertTrue(seconds < 2) // Should be very recent
        }

        @Test
        @DisplayName("should return correct value for old heartbeat")
        fun testOldHeartbeat() {
            val thirtySecondsAgo = Instant.now().minusSeconds(30)
            val serverInfo = ServerInfo(
                serverId = "server-1",
                lastHeartbeat = thirtySecondsAgo,
                playerCount = 10,
                isHealthy = false
            )

            val seconds = serverInfo.secondsSinceHeartbeat()

            assertTrue(seconds >= 29)
            assertTrue(seconds <= 31)
        }
    }

    @Nested
    @DisplayName("Data Class Properties")
    inner class DataClassTests {

        @Test
        @DisplayName("should preserve all properties")
        fun testPropertyPreservation() {
            val timestamp = Instant.ofEpochMilli(1700000000000L)
            val serverInfo = ServerInfo(
                serverId = "test-server",
                lastHeartbeat = timestamp,
                playerCount = 42,
                isHealthy = true
            )

            assertEquals("test-server", serverInfo.serverId)
            assertEquals(timestamp, serverInfo.lastHeartbeat)
            assertEquals(42, serverInfo.playerCount)
            assertTrue(serverInfo.isHealthy)
        }

        @Test
        @DisplayName("equal servers should be equal")
        fun testEquality() {
            val timestamp = Instant.ofEpochMilli(1700000000000L)
            val server1 = ServerInfo("server-1", timestamp, 10, true)
            val server2 = ServerInfo("server-1", timestamp, 10, true)

            assertEquals(server1, server2)
        }

        @Test
        @DisplayName("servers with different IDs should not be equal")
        fun testInequality() {
            val timestamp = Instant.ofEpochMilli(1700000000000L)
            val server1 = ServerInfo("server-1", timestamp, 10, true)
            val server2 = ServerInfo("server-2", timestamp, 10, true)

            assertNotEquals(server1, server2)
        }

        @Test
        @DisplayName("copy should work correctly")
        fun testCopy() {
            val original = ServerInfo("server-1", Instant.now(), 10, true)
            val copy = original.copy(playerCount = 20)

            assertEquals(original.serverId, copy.serverId)
            assertEquals(original.lastHeartbeat, copy.lastHeartbeat)
            assertEquals(20, copy.playerCount)
            assertEquals(original.isHealthy, copy.isHealthy)
        }
    }

    @Nested
    @DisplayName("Health Boundary Conditions")
    inner class HealthBoundaryTests {

        @Test
        @DisplayName("should be healthy when exactly at timeout boundary")
        fun testHealthyAtBoundary() {
            // Heartbeat exactly 89 seconds ago (just under 90s timeout)
            val heartbeatTime = System.currentTimeMillis() - 89_000
            val serverInfo = ServerInfo.fromHeartbeat(
                serverId = "server-1",
                timestamp = heartbeatTime,
                playerCount = 5,
                timeoutSeconds = 90
            )

            assertTrue(serverInfo.isHealthy)
        }

        @Test
        @DisplayName("should be unhealthy when just past timeout boundary")
        fun testUnhealthyPastBoundary() {
            // Heartbeat exactly 91 seconds ago (just over 90s timeout)
            val heartbeatTime = System.currentTimeMillis() - 91_000
            val serverInfo = ServerInfo.fromHeartbeat(
                serverId = "server-1",
                timestamp = heartbeatTime,
                playerCount = 5,
                timeoutSeconds = 90
            )

            assertFalse(serverInfo.isHealthy)
        }
    }
}
