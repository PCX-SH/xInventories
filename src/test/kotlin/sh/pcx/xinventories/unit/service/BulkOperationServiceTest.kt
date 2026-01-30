package sh.pcx.xinventories.unit.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.service.BulkOperationProgress
import sh.pcx.xinventories.internal.service.BulkOperationResult
import sh.pcx.xinventories.internal.service.BulkOperationStatus
import sh.pcx.xinventories.internal.service.BulkOperationType
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("BulkOperationService Components")
class BulkOperationServiceTest {

    @Nested
    @DisplayName("BulkOperationType")
    inner class BulkOperationTypeTests {

        @Test
        @DisplayName("all operation types are defined")
        fun allOperationTypesAreDefined() {
            assertEquals(4, BulkOperationType.entries.size)
            assertNotNull(BulkOperationType.CLEAR)
            assertNotNull(BulkOperationType.APPLY_TEMPLATE)
            assertNotNull(BulkOperationType.RESET_STATS)
            assertNotNull(BulkOperationType.EXPORT)
        }
    }

    @Nested
    @DisplayName("BulkOperationStatus")
    inner class BulkOperationStatusTests {

        @Test
        @DisplayName("all status values are defined")
        fun allStatusValuesAreDefined() {
            assertEquals(4, BulkOperationStatus.entries.size)
            assertNotNull(BulkOperationStatus.RUNNING)
            assertNotNull(BulkOperationStatus.COMPLETED)
            assertNotNull(BulkOperationStatus.FAILED)
            assertNotNull(BulkOperationStatus.CANCELLED)
        }
    }

    @Nested
    @DisplayName("BulkOperationProgress")
    inner class BulkOperationProgressTests {

        @Test
        @DisplayName("initial state is correct")
        fun initialStateIsCorrect() {
            val progress = BulkOperationProgress(
                operationId = "test-1",
                operationType = BulkOperationType.CLEAR,
                group = "survival",
                totalPlayers = 100
            )

            assertEquals("test-1", progress.operationId)
            assertEquals(BulkOperationType.CLEAR, progress.operationType)
            assertEquals("survival", progress.group)
            assertEquals(100, progress.totalPlayers)
            assertEquals(0, progress.processed)
            assertEquals(0, progress.successful)
            assertEquals(0, progress.failed)
            assertEquals(0, progress.percentComplete)
            assertEquals(BulkOperationStatus.RUNNING, progress.status)
            assertFalse(progress.isComplete)
            assertNull(progress.endTime)
            assertNull(progress.error)
        }

        @Test
        @DisplayName("markSuccess increments counters")
        fun markSuccessIncrementsCounters() {
            val progress = BulkOperationProgress(
                operationId = "test-1",
                operationType = BulkOperationType.CLEAR,
                group = "survival",
                totalPlayers = 10
            )

            progress.markSuccess()
            progress.markSuccess()
            progress.markSuccess()

            assertEquals(3, progress.processed)
            assertEquals(3, progress.successful)
            assertEquals(0, progress.failed)
            assertEquals(30, progress.percentComplete)
        }

        @Test
        @DisplayName("markFailure increments counters")
        fun markFailureIncrementsCounters() {
            val progress = BulkOperationProgress(
                operationId = "test-1",
                operationType = BulkOperationType.CLEAR,
                group = "survival",
                totalPlayers = 10
            )

            progress.markFailure()
            progress.markFailure()

            assertEquals(2, progress.processed)
            assertEquals(0, progress.successful)
            assertEquals(2, progress.failed)
            assertEquals(20, progress.percentComplete)
        }

        @Test
        @DisplayName("mixed success and failure")
        fun mixedSuccessAndFailure() {
            val progress = BulkOperationProgress(
                operationId = "test-1",
                operationType = BulkOperationType.CLEAR,
                group = "survival",
                totalPlayers = 10
            )

            progress.markSuccess()
            progress.markFailure()
            progress.markSuccess()
            progress.markSuccess()
            progress.markFailure()

            assertEquals(5, progress.processed)
            assertEquals(3, progress.successful)
            assertEquals(2, progress.failed)
            assertEquals(50, progress.percentComplete)
        }

        @Test
        @DisplayName("complete marks operation as done")
        fun completeMarksOperationAsDone() {
            val progress = BulkOperationProgress(
                operationId = "test-1",
                operationType = BulkOperationType.CLEAR,
                group = "survival",
                totalPlayers = 10
            )

            progress.complete()

            assertEquals(BulkOperationStatus.COMPLETED, progress.status)
            assertTrue(progress.isComplete)
            assertNotNull(progress.endTime)
        }

        @Test
        @DisplayName("complete with custom status")
        fun completeWithCustomStatus() {
            val progress = BulkOperationProgress(
                operationId = "test-1",
                operationType = BulkOperationType.CLEAR,
                group = "survival",
                totalPlayers = 10
            )

            progress.complete(BulkOperationStatus.CANCELLED)

            assertEquals(BulkOperationStatus.CANCELLED, progress.status)
            assertTrue(progress.isComplete)
        }

        @Test
        @DisplayName("percent complete handles zero total")
        fun percentCompleteHandlesZeroTotal() {
            val progress = BulkOperationProgress(
                operationId = "test-1",
                operationType = BulkOperationType.CLEAR,
                group = "survival",
                totalPlayers = 0
            )

            assertEquals(0, progress.percentComplete)
        }

        @Test
        @DisplayName("isComplete reflects status")
        fun isCompleteReflectsStatus() {
            val progress = BulkOperationProgress(
                operationId = "test-1",
                operationType = BulkOperationType.CLEAR,
                group = "survival",
                totalPlayers = 10
            )

            assertFalse(progress.isComplete)

            progress.status = BulkOperationStatus.COMPLETED
            assertTrue(progress.isComplete)

            progress.status = BulkOperationStatus.FAILED
            assertTrue(progress.isComplete)

            progress.status = BulkOperationStatus.CANCELLED
            assertTrue(progress.isComplete)

            progress.status = BulkOperationStatus.RUNNING
            assertFalse(progress.isComplete)
        }
    }

    @Nested
    @DisplayName("BulkOperationResult")
    inner class BulkOperationResultTests {

        @Test
        @DisplayName("captures all result data")
        fun capturesAllResultData() {
            val result = BulkOperationResult(
                operationId = "test-1",
                operationType = BulkOperationType.APPLY_TEMPLATE,
                group = "creative",
                totalPlayers = 50,
                successCount = 48,
                failCount = 2,
                durationMs = 5000,
                status = BulkOperationStatus.COMPLETED,
                error = null
            )

            assertEquals("test-1", result.operationId)
            assertEquals(BulkOperationType.APPLY_TEMPLATE, result.operationType)
            assertEquals("creative", result.group)
            assertEquals(50, result.totalPlayers)
            assertEquals(48, result.successCount)
            assertEquals(2, result.failCount)
            assertEquals(5000, result.durationMs)
            assertEquals(BulkOperationStatus.COMPLETED, result.status)
            assertNull(result.error)
        }

        @Test
        @DisplayName("captures error message on failure")
        fun capturesErrorMessageOnFailure() {
            val result = BulkOperationResult(
                operationId = "test-2",
                operationType = BulkOperationType.EXPORT,
                group = "survival",
                totalPlayers = 0,
                successCount = 0,
                failCount = 0,
                durationMs = 100,
                status = BulkOperationStatus.FAILED,
                error = "Template not found"
            )

            assertEquals(BulkOperationStatus.FAILED, result.status)
            assertEquals("Template not found", result.error)
        }
    }
}
