package com.example

import android.net.Uri
import com.example.model.PdfProcessTask
import com.example.model.ProcessingStage
import com.example.model.ProductItem
import com.example.model.StepStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testNamingPatternInterpolation() {
        val originalBase = "Carrefour_Week_34"
        val date = "2026-08-21"
        val index = String.format(Locale.US, "%02d", 1)
        val brand = "Carrefour"
        val count = "35"

        val pattern = "{brand}_{date}_{index}_{count}items"
        val result = pattern
            .replace("{name}", originalBase, ignoreCase = true)
            .replace("{date}", date, ignoreCase = true)
            .replace("{index}", index, ignoreCase = true)
            .replace("{brand}", brand, ignoreCase = true)
            .replace("{count}", count, ignoreCase = true)

        assertEquals("Carrefour_2026-08-21_01_35items", result)
    }

    @Test
    fun testExtractionStepsStateCalculation() {
        val task = PdfProcessTask(
            id = "test_1",
            fileName = "flyer.pdf",
            fileUri = Uri.parse("content://dummy/flyer.pdf"),
            fileSizeByte = 15 * 1024 * 1024,
            totalPages = 12,
            totalChunks = 3,
            currentChunk = 2,
            stage = ProcessingStage.Extracting(2, 3, 18),
            progress = 0.45f
        )

        val steps = task.getExtractionSteps()
        assertEquals(6, steps.size)

        // Step 1: Inspect -> COMPLETED
        assertEquals(StepStatus.COMPLETED, steps[0].status)
        assertEquals("PDF Structure Analysis", steps[0].title)

        // Step 2: Chunker -> COMPLETED
        assertEquals(StepStatus.COMPLETED, steps[1].status)

        // Step 3: AI Extract -> IN_PROGRESS
        assertEquals(StepStatus.IN_PROGRESS, steps[2].status)
        assertTrue(steps[2].detail?.contains("Chunk 2 of 3") == true)
        assertTrue(steps[2].detail?.contains("18 items") == true)

        // Step 4: Validate -> PENDING
        assertEquals(StepStatus.PENDING, steps[3].status)

        // Step 5: Export -> PENDING
        assertEquals(StepStatus.PENDING, steps[4].status)

        // Step 6: Sync -> PENDING
        assertEquals(StepStatus.PENDING, steps[5].status)
    }

    @Test
    fun testExtractionStepsCompletedState() {
        val task = PdfProcessTask(
            id = "test_2",
            fileName = "complete.pdf",
            fileUri = Uri.parse("content://dummy/complete.pdf"),
            fileSizeByte = 2 * 1024 * 1024,
            stage = ProcessingStage.Completed,
            products = listOf(
                ProductItem(name = "Milk", price = 1.99)
            ),
            progress = 1.0f
        )

        val steps = task.getExtractionSteps()
        assertEquals(6, steps.size)
        assertTrue(steps.all { it.status == StepStatus.COMPLETED })
    }

    @Test
    fun testCaseTransformations() {
        val input = "Carrefour Super Promo"
        val snake = input.replace(Regex("[\\s\\-]+"), "_").lowercase(Locale.getDefault())
        val kebab = input.replace(Regex("[\\s_]+"), "-").lowercase(Locale.getDefault())

        assertEquals("carrefour_super_promo", snake)
        assertEquals("carrefour-super-promo", kebab)
    }
}

