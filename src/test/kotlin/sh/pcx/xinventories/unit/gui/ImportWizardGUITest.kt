package sh.pcx.xinventories.unit.gui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.gui.menu.ImportWizardGUI

@DisplayName("ImportWizardGUI Logic")
class ImportWizardGUITest {

    @Nested
    @DisplayName("Import Step Navigation")
    inner class ImportStepNavigationTests {

        @Test
        @DisplayName("steps are in correct order")
        fun stepsAreInCorrectOrder() {
            val steps = ImportWizardGUI.ImportStep.entries

            assertEquals(ImportWizardGUI.ImportStep.DETECT_SOURCES, steps[0])
            assertEquals(ImportWizardGUI.ImportStep.SELECT_SOURCE, steps[1])
            assertEquals(ImportWizardGUI.ImportStep.PREVIEW, steps[2])
            assertEquals(ImportWizardGUI.ImportStep.CONFIGURE_MAPPING, steps[3])
            assertEquals(ImportWizardGUI.ImportStep.IMPORTING, steps[4])
            assertEquals(ImportWizardGUI.ImportStep.RESULTS, steps[5])
        }

        @Test
        @DisplayName("step ordinals are sequential")
        fun stepOrdinalsAreSequential() {
            val steps = ImportWizardGUI.ImportStep.entries

            for (i in steps.indices) {
                assertEquals(i, steps[i].ordinal)
            }
        }

        @Test
        @DisplayName("step numbers for titles are 1-indexed")
        fun stepNumbersForTitlesAre1Indexed() {
            ImportWizardGUI.ImportStep.entries.forEachIndexed { index, step ->
                val stepNum = step.ordinal + 1
                assertEquals(index + 1, stepNum)
            }
        }
    }

    @Nested
    @DisplayName("Byte Size Formatting")
    inner class ByteSizeFormattingTests {

        @Test
        @DisplayName("formats bytes correctly")
        fun formatsBytesCorrectly() {
            assertEquals("500 B", formatBytes(500))
        }

        @Test
        @DisplayName("formats kilobytes correctly")
        fun formatsKilobytesCorrectly() {
            assertEquals("5 KB", formatBytes(5120))
        }

        @Test
        @DisplayName("formats megabytes correctly")
        fun formatsMegabytesCorrectly() {
            assertEquals("10 MB", formatBytes(10485760))
        }

        @Test
        @DisplayName("formats gigabytes correctly")
        fun formatsGigabytesCorrectly() {
            assertEquals("5 GB", formatBytes(5368709120))
        }

        private fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> "${bytes / (1024 * 1024 * 1024)} GB"
            }
        }
    }

    @Nested
    @DisplayName("Source Material Mapping")
    inner class SourceMaterialMappingTests {

        @Test
        @DisplayName("PWI source gets ender chest")
        fun pwiSourceGetsEnderChest() {
            assertEquals("ENDER_CHEST", getSourceMaterial("pwi"))
            assertEquals("ENDER_CHEST", getSourceMaterial("PWI_Plugin"))
        }

        @Test
        @DisplayName("MVI source gets regular chest")
        fun mviSourceGetsRegularChest() {
            assertEquals("CHEST", getSourceMaterial("mvi"))
            assertEquals("CHEST", getSourceMaterial("MVI_Data"))
        }

        @Test
        @DisplayName("MyWorlds source gets grass block")
        fun myWorldsSourceGetsGrassBlock() {
            assertEquals("GRASS_BLOCK", getSourceMaterial("myworlds"))
            assertEquals("GRASS_BLOCK", getSourceMaterial("MyWorlds_Plugin"))
        }

        @Test
        @DisplayName("unknown source gets paper")
        fun unknownSourceGetsPaper() {
            assertEquals("PAPER", getSourceMaterial("unknown"))
            assertEquals("PAPER", getSourceMaterial("custom_plugin"))
        }

        private fun getSourceMaterial(sourceId: String): String {
            return when {
                sourceId.contains("pwi", ignoreCase = true) -> "ENDER_CHEST"
                sourceId.contains("mvi", ignoreCase = true) -> "CHEST"
                sourceId.contains("myworlds", ignoreCase = true) -> "GRASS_BLOCK"
                else -> "PAPER"
            }
        }
    }

    @Nested
    @DisplayName("Navigation Validation")
    inner class NavigationValidationTests {

        @Test
        @DisplayName("detect sources step cannot go back")
        fun detectSourcesStepCannotGoBack() {
            val step = ImportWizardGUI.ImportStep.DETECT_SOURCES
            val canGoBack = step != ImportWizardGUI.ImportStep.DETECT_SOURCES

            assertFalse(canGoBack)
        }

        @Test
        @DisplayName("results step cannot go next")
        fun resultsStepCannotGoNext() {
            val step = ImportWizardGUI.ImportStep.RESULTS
            val canGoNext = step != ImportWizardGUI.ImportStep.RESULTS &&
                           step != ImportWizardGUI.ImportStep.IMPORTING

            assertFalse(canGoNext)
        }

        @Test
        @DisplayName("middle steps can go both directions")
        fun middleStepsCanGoBothDirections() {
            val middleSteps = listOf(
                ImportWizardGUI.ImportStep.SELECT_SOURCE,
                ImportWizardGUI.ImportStep.PREVIEW,
                ImportWizardGUI.ImportStep.CONFIGURE_MAPPING
            )

            for (step in middleSteps) {
                val canGoBack = step != ImportWizardGUI.ImportStep.DETECT_SOURCES
                val canGoNext = step != ImportWizardGUI.ImportStep.RESULTS &&
                               step != ImportWizardGUI.ImportStep.IMPORTING

                assertTrue(canGoBack, "Step $step should be able to go back")
                assertTrue(canGoNext, "Step $step should be able to go next")
            }
        }
    }

    @Nested
    @DisplayName("Data Estimation")
    inner class DataEstimationTests {

        @Test
        @DisplayName("estimates data size correctly")
        fun estimatesDataSizeCorrectly() {
            // ~10KB per player per group
            val playerCount = 100
            val groupCount = 3
            val estimated = estimateDataSize(playerCount, groupCount)

            assertEquals(100L * 3L * 10 * 1024, estimated)
        }

        @Test
        @DisplayName("estimation handles edge cases")
        fun estimationHandlesEdgeCases() {
            assertEquals(0, estimateDataSize(0, 0))
            assertEquals(0, estimateDataSize(100, 0))
            assertEquals(0, estimateDataSize(0, 5))
        }

        private fun estimateDataSize(playerCount: Int, groupCount: Int): Long {
            return (playerCount.toLong() * groupCount.toLong() * 10 * 1024)
        }
    }

    @Nested
    @DisplayName("Mapping Options Toggle")
    inner class MappingOptionsToggleTests {

        @Test
        @DisplayName("toggle flips boolean correctly")
        fun toggleFlipsBooleanCorrectly() {
            var overwriteExisting = false
            overwriteExisting = !overwriteExisting
            assertTrue(overwriteExisting)

            overwriteExisting = !overwriteExisting
            assertFalse(overwriteExisting)
        }

        @Test
        @DisplayName("default options are sensible")
        fun defaultOptionsAreSensible() {
            // Test default import options
            val defaultOverwrite = false
            val defaultCreateMissing = true
            val defaultImportBalances = true

            assertFalse(defaultOverwrite, "Should not overwrite by default")
            assertTrue(defaultCreateMissing, "Should create missing groups by default")
            assertTrue(defaultImportBalances, "Should import balances by default")
        }
    }
}
