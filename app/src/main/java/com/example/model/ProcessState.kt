package com.example.model

import android.net.Uri

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class QueueExecutionState {
    IDLE,
    PROCESSING,
    PAUSED
}

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED
}

data class ExtractionStepItem(
    val stepIndex: Int,
    val stepKey: String,
    val title: String,
    val summary: String,
    val status: StepStatus,
    val detail: String? = null,
    val progress: Float = 0f
)

sealed class ProcessingStage {
    object Queued : ProcessingStage()
    object Analyzing : ProcessingStage()
    data class Splitting(val chunkIndex: Int, val totalChunks: Int) : ProcessingStage()
    data class Extracting(val chunkIndex: Int, val totalChunks: Int, val itemsFoundSoFar: Int = 0) : ProcessingStage()
    data class Validating(val itemsCount: Int = 0) : ProcessingStage()
    data class GeneratingFiles(val itemsCount: Int = 0) : ProcessingStage()
    data class UploadingDrive(val statusText: String = "Uploading to Google Drive...") : ProcessingStage()
    object Completed : ProcessingStage()
    data class Error(val message: String) : ProcessingStage()

    fun label(): String = when (this) {
        is Queued -> "Queued"
        is Analyzing -> "Step 1/6: Analyzing PDF structure & pages..."
        is Splitting -> "Step 2/6: Splitting PDF into chunks ($chunkIndex/$totalChunks)..."
        is Extracting -> if (itemsFoundSoFar > 0) {
            "Step 3/6: Gemini AI Extracting ($chunkIndex/$totalChunks • $itemsFoundSoFar items)..."
        } else {
            "Step 3/6: Gemini AI Extracting ($chunkIndex/$totalChunks)..."
        }
        is Validating -> "Step 4/6: Validating $itemsCount products with schema rules..."
        is GeneratingFiles -> "Step 5/6: Generating Excel & CSV files..."
        is UploadingDrive -> "Step 6/6: $statusText"
        is Completed -> "Completed (All 6 steps finished)"
        is Error -> "Error: $message"
    }

    fun stepNumber(): Int = when (this) {
        is Queued -> 0
        is Analyzing -> 1
        is Splitting -> 2
        is Extracting -> 3
        is Validating -> 4
        is GeneratingFiles -> 5
        is UploadingDrive -> 6
        is Completed -> 6
        is Error -> 0
    }

    val isTerminal: Boolean
        get() = this is Completed || this is Error
}

data class PdfProcessTask(
    val id: String,
    val fileName: String,
    val fileUri: Uri,
    val fileSizeByte: Long,
    val totalPages: Int = 0,
    val totalChunks: Int = 1,
    val currentChunk: Int = 0,
    val stage: ProcessingStage = ProcessingStage.Queued,
    val progress: Float = 0f,
    val currentStepDetail: String? = null,
    val stepLogs: List<String> = emptyList(),
    val products: List<ProductItem> = emptyList(),
    val schemaId: String = DefaultSchemas.ID_SUPERMARKET,
    val schemaName: String = "Supermarket Flyer",
    val xlsxFilePath: String? = null,
    val csvFilePath: String? = null,
    val driveXlsxUrl: String? = null,
    val driveCsvUrl: String? = null,
    val driveError: String? = null,
    val errorMessage: String? = null,
    val enqueuedAtMs: Long = System.currentTimeMillis()
) {
    val formattedSize: String
        get() {
            val mb = fileSizeByte / (1024.0 * 1024.0)
            return if (mb >= 1.0) "%.1f MB".format(mb) else "${fileSizeByte / 1024} KB"
        }

    val isLargeFile: Boolean
        get() = fileSizeByte > 50 * 1024 * 1024 // > 50 MB

    fun getExtractionSteps(): List<ExtractionStepItem> {
        val currentStep = stage.stepNumber()
        val isError = stage is ProcessingStage.Error
        val isDone = stage is ProcessingStage.Completed

        fun stepState(stepNum: Int): StepStatus = when {
            isDone -> StepStatus.COMPLETED
            isError && currentStep == stepNum -> StepStatus.FAILED
            isError && currentStep > stepNum -> StepStatus.COMPLETED
            isError -> StepStatus.SKIPPED
            currentStep > stepNum -> StepStatus.COMPLETED
            currentStep == stepNum -> StepStatus.IN_PROGRESS
            else -> StepStatus.PENDING
        }

        val step1Detail = when {
            totalPages > 0 -> "$totalPages pages detected (${formattedSize})"
            stage is ProcessingStage.Analyzing -> "Reading PDF catalog structure..."
            currentStep > 1 -> "$totalPages pages parsed"
            else -> "Inspect document geometry & pages"
        }

        val step2Detail = when {
            totalChunks > 0 && currentStep >= 2 -> "$totalChunks memory-safe chunk(s) prepared"
            stage is ProcessingStage.Splitting -> "Partitioning into chunks (${stage.chunkIndex}/$totalChunks)..."
            else -> "Buffered chunk streaming"
        }

        val step3Detail = when {
            stage is ProcessingStage.Extracting -> "Chunk ${stage.chunkIndex} of $totalChunks • ${stage.itemsFoundSoFar} items found"
            currentStep > 3 -> "${products.size} product items extracted with Gemini AI"
            else -> "Multimodal vision & schema extraction"
        }

        val step4Detail = when {
            stage is ProcessingStage.Validating -> "Validating ${stage.itemsCount} products against active regex rules..."
            currentStep > 4 -> "Prices, dates & units validated"
            else -> "Data normalization & cleaning"
        }

        val step5Detail = when {
            stage is ProcessingStage.GeneratingFiles -> "Generating formatted .xlsx & .csv workbooks..."
            xlsxFilePath != null || currentStep > 5 -> "Excel & CSV compiled successfully"
            else -> "Excel & CSV workbook generation"
        }

        val step6Detail = when {
            stage is ProcessingStage.UploadingDrive -> "Uploading to Google Drive folder..."
            driveXlsxUrl != null -> "Backed up to Google Drive & local Room DB"
            driveError != null -> "Drive upload skipped: $driveError"
            isDone -> "Saved to local Room DB"
            else -> "Google Drive & SQLite persistence"
        }

        return listOf(
            ExtractionStepItem(
                stepIndex = 1,
                stepKey = "inspect",
                title = "PDF Structure Analysis",
                summary = "Inspects PDF metadata, dimensions, and total page count.",
                status = stepState(1),
                detail = step1Detail,
                progress = if (currentStep > 1) 1f else if (currentStep == 1) 0.5f else 0f
            ),
            ExtractionStepItem(
                stepIndex = 2,
                stepKey = "chunk",
                title = "Document Chunk Streaming",
                summary = "Splits large catalogs into memory-buffered partitions to prevent OOM.",
                status = stepState(2),
                detail = step2Detail,
                progress = if (currentStep > 2) 1f else if (currentStep == 2) (currentChunk.toFloat() / totalChunks.coerceAtLeast(1)) else 0f
            ),
            ExtractionStepItem(
                stepIndex = 3,
                stepKey = "ai_extract",
                title = "Gemini Multimodal AI Extraction",
                summary = "Extracts structured product catalog records using schema definitions.",
                status = stepState(3),
                detail = step3Detail,
                progress = if (currentStep > 3) 1f else if (currentStep == 3) (currentChunk.toFloat() / totalChunks.coerceAtLeast(1)) else 0f
            ),
            ExtractionStepItem(
                stepIndex = 4,
                stepKey = "validate",
                title = "Regex Field Validation & Cleaning",
                summary = "Enforces price, promotion dates, unit, and category consistency.",
                status = stepState(4),
                detail = step4Detail,
                progress = if (currentStep > 4) 1f else if (currentStep == 4) 0.7f else 0f
            ),
            ExtractionStepItem(
                stepIndex = 5,
                stepKey = "export",
                title = "Excel & CSV Compilation",
                summary = "Generates formatted .xlsx workbooks and clean UTF-8 .csv datasets.",
                status = stepState(5),
                detail = step5Detail,
                progress = if (currentStep > 5) 1f else if (currentStep == 5) 0.8f else 0f
            ),
            ExtractionStepItem(
                stepIndex = 6,
                stepKey = "sync",
                title = "Cloud Sync & Room Database",
                summary = "Synchronizes outputs to Google Drive and local Room database records.",
                status = stepState(6),
                detail = step6Detail,
                progress = if (isDone) 1f else if (currentStep == 6) 0.9f else 0f
            )
        )
    }
}

data class SupermarketInfo(
    val name: String,
    val websiteUrl: String,
    val logoResName: String,
    val description: String
)

val MAURITIUS_SUPERMARKETS = listOf(
    SupermarketInfo(
        name = "Dream Price",
        websiteUrl = "https://e-brochures.mu/dream-price",
        logoResName = "ic_supermarket",
        description = "Popular Mauritian supermarket chain offering weekly discount e-brochures."
    ),
    SupermarketInfo(
        name = "Family Supermarket",
        websiteUrl = "https://e-brochures.mu/family-supermarket",
        logoResName = "ic_supermarket",
        description = "Local family grocery deals and promotional PDF flyers."
    ),
    SupermarketInfo(
        name = "GSR Supermarket",
        websiteUrl = "https://e-brochures.mu/gsr",
        logoResName = "ic_supermarket",
        description = "Great Save Retail brochures with daily food and household specials."
    ),
    SupermarketInfo(
        name = "Intermart",
        websiteUrl = "https://e-brochures.mu/intermart",
        logoResName = "ic_supermarket",
        description = "Major supermarket hypermarket catalogues and promotional magazine."
    ),
    SupermarketInfo(
        name = "King Savers",
        websiteUrl = "https://e-brochures.mu/kingsavers",
        logoResName = "ic_supermarket",
        description = "Discount supermarket catalogue with latest offer leaflets."
    ),
    SupermarketInfo(
        name = "Lolo Supermarket",
        websiteUrl = "https://e-brochures.mu/lolo",
        logoResName = "ic_supermarket",
        description = "Lolo Hypermarket deals and monthly brochure offers."
    ),
    SupermarketInfo(
        name = "Super U",
        websiteUrl = "https://e-brochures.mu/super-u",
        logoResName = "ic_supermarket",
        description = "Super U hypermarkets weekly brochure and store deals."
    ),
    SupermarketInfo(
        name = "Way Supermarket",
        websiteUrl = "https://e-brochures.mu/way",
        logoResName = "ic_supermarket",
        description = "Way supermarket promotional catalogue and product price lists."
    )
)
