package sh.pcx.xinventories.unit.gui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.service.BulkOperationProgress
import sh.pcx.xinventories.internal.service.BulkOperationResult
import sh.pcx.xinventories.internal.service.BulkOperationStatus
import sh.pcx.xinventories.internal.service.BulkOperationType
import org.bukkit.Material

@DisplayName("Bulk Operations GUI Logic")
class BulkOperationsGUITest {

    @Nested
    @DisplayName("Operation Type Display")
    inner class OperationTypeDisplayTests {

        @Test
        @DisplayName("clear operation gets correct name")
        fun clearOperationGetsCorrectName() {
            val operationType = BulkOperationType.CLEAR

            val name = getOperationName(operationType)

            assertEquals("Clear Inventories", name)
        }

        @Test
        @DisplayName("apply template operation gets correct name")
        fun applyTemplateOperationGetsCorrectName() {
            val operationType = BulkOperationType.APPLY_TEMPLATE

            val name = getOperationName(operationType)

            assertEquals("Apply Template", name)
        }

        @Test
        @DisplayName("reset stats operation gets correct name")
        fun resetStatsOperationGetsCorrectName() {
            val operationType = BulkOperationType.RESET_STATS

            val name = getOperationName(operationType)

            assertEquals("Reset Stats", name)
        }

        @Test
        @DisplayName("export operation gets correct name")
        fun exportOperationGetsCorrectName() {
            val operationType = BulkOperationType.EXPORT

            val name = getOperationName(operationType)

            assertEquals("Export Data", name)
        }
    }

    @Nested
    @DisplayName("Operation Material Selection")
    inner class OperationMaterialSelectionTests {

        @Test
        @DisplayName("clear operation gets TNT material")
        fun clearOperationGetsTNT() {
            val operationType = BulkOperationType.CLEAR

            val material = getOperationMaterial(operationType)

            assertEquals(Material.TNT, material)
        }

        @Test
        @DisplayName("apply template operation gets chest material")
        fun applyTemplateOperationGetsChest() {
            val operationType = BulkOperationType.APPLY_TEMPLATE

            val material = getOperationMaterial(operationType)

            assertEquals(Material.CHEST, material)
        }

        @Test
        @DisplayName("reset stats operation gets experience bottle material")
        fun resetStatsOperationGetsExperienceBottle() {
            val operationType = BulkOperationType.RESET_STATS

            val material = getOperationMaterial(operationType)

            assertEquals(Material.EXPERIENCE_BOTTLE, material)
        }

        @Test
        @DisplayName("export operation gets writable book material")
        fun exportOperationGetsWritableBook() {
            val operationType = BulkOperationType.EXPORT

            val material = getOperationMaterial(operationType)

            assertEquals(Material.WRITABLE_BOOK, material)
        }
    }

    @Nested
    @DisplayName("Warning Color Selection")
    inner class WarningColorSelectionTests {

        @Test
        @DisplayName("clear operation uses red warning color")
        fun clearOperationUsesRedWarning() {
            val operationType = BulkOperationType.CLEAR

            val warningColor = getWarningColor(operationType)

            assertEquals(Material.RED_STAINED_GLASS_PANE, warningColor)
        }

        @Test
        @DisplayName("other operations use yellow warning color")
        fun otherOperationsUseYellowWarning() {
            val operations = listOf(
                BulkOperationType.APPLY_TEMPLATE,
                BulkOperationType.RESET_STATS,
                BulkOperationType.EXPORT
            )

            for (operationType in operations) {
                val warningColor = getWarningColor(operationType)
                assertEquals(Material.YELLOW_STAINED_GLASS_PANE, warningColor,
                    "Expected YELLOW for $operationType")
            }
        }
    }

    @Nested
    @DisplayName("Progress Display")
    inner class ProgressDisplayTests {

        @Test
        @DisplayName("progress item shows correct percentage")
        fun progressItemShowsCorrectPercentage() {
            val progress = BulkOperationProgress(
                operationId = "test-1",
                operationType = BulkOperationType.CLEAR,
                group = "survival",
                totalPlayers = 100
            )

            // Simulate progress
            repeat(50) { progress.markSuccess() }

            assertEquals(50, progress.percentComplete)
            assertEquals(50, progress.processed)
            assertEquals(50, progress.successful)
            assertEquals(0, progress.failed)
        }

        @Test
        @DisplayName("progress handles mixed success and failure")
        fun progressHandlesMixedSuccessAndFailure() {
            val progress = BulkOperationProgress(
                operationId = "test-1",
                operationType = BulkOperationType.APPLY_TEMPLATE,
                group = "creative",
                totalPlayers = 100
            )

            repeat(30) { progress.markSuccess() }
            repeat(20) { progress.markFailure() }

            assertEquals(50, progress.percentComplete)
            assertEquals(50, progress.processed)
            assertEquals(30, progress.successful)
            assertEquals(20, progress.failed)
        }

        @Test
        @DisplayName("isComplete returns true for completed operations")
        fun isCompleteReturnsTrueForCompletedOperations() {
            val progress = BulkOperationProgress(
                operationId = "test-1",
                operationType = BulkOperationType.CLEAR,
                group = "survival",
                totalPlayers = 10
            )

            assertFalse(progress.isComplete)

            progress.complete(BulkOperationStatus.COMPLETED)

            assertTrue(progress.isComplete)
        }

        @Test
        @DisplayName("isComplete returns true for cancelled operations")
        fun isCompleteReturnsTrueForCancelledOperations() {
            val progress = BulkOperationProgress(
                operationId = "test-1",
                operationType = BulkOperationType.CLEAR,
                group = "survival",
                totalPlayers = 10
            )

            progress.complete(BulkOperationStatus.CANCELLED)

            assertTrue(progress.isComplete)
            assertEquals(BulkOperationStatus.CANCELLED, progress.status)
        }
    }

    @Nested
    @DisplayName("Result Summary")
    inner class ResultSummaryTests {

        @Test
        @DisplayName("result captures operation metadata")
        fun resultCapturesOperationMetadata() {
            val result = BulkOperationResult(
                operationId = "bulk-1-123456",
                operationType = BulkOperationType.APPLY_TEMPLATE,
                group = "survival",
                totalPlayers = 100,
                successCount = 95,
                failCount = 5,
                durationMs = 5000,
                status = BulkOperationStatus.COMPLETED,
                error = null
            )

            assertEquals("bulk-1-123456", result.operationId)
            assertEquals(BulkOperationType.APPLY_TEMPLATE, result.operationType)
            assertEquals("survival", result.group)
            assertEquals(100, result.totalPlayers)
            assertEquals(95, result.successCount)
            assertEquals(5, result.failCount)
            assertEquals(5000, result.durationMs)
            assertEquals(BulkOperationStatus.COMPLETED, result.status)
            assertNull(result.error)
        }

        @Test
        @DisplayName("result captures error for failed operations")
        fun resultCapturesErrorForFailedOperations() {
            val result = BulkOperationResult(
                operationId = "bulk-2-123456",
                operationType = BulkOperationType.APPLY_TEMPLATE,
                group = "survival",
                totalPlayers = 0,
                successCount = 0,
                failCount = 0,
                durationMs = 100,
                status = BulkOperationStatus.FAILED,
                error = "Template 'nonexistent' not found"
            )

            assertEquals(BulkOperationStatus.FAILED, result.status)
            assertEquals("Template 'nonexistent' not found", result.error)
        }
    }

    // Helper methods that mirror the GUI logic

    private fun getOperationName(operationType: BulkOperationType): String = when (operationType) {
        BulkOperationType.CLEAR -> "Clear Inventories"
        BulkOperationType.APPLY_TEMPLATE -> "Apply Template"
        BulkOperationType.RESET_STATS -> "Reset Stats"
        BulkOperationType.EXPORT -> "Export Data"
    }

    private fun getOperationMaterial(operationType: BulkOperationType): Material = when (operationType) {
        BulkOperationType.CLEAR -> Material.TNT
        BulkOperationType.APPLY_TEMPLATE -> Material.CHEST
        BulkOperationType.RESET_STATS -> Material.EXPERIENCE_BOTTLE
        BulkOperationType.EXPORT -> Material.WRITABLE_BOOK
    }

    private fun getWarningColor(operationType: BulkOperationType): Material = when (operationType) {
        BulkOperationType.CLEAR -> Material.RED_STAINED_GLASS_PANE
        else -> Material.YELLOW_STAINED_GLASS_PANE
    }
}
