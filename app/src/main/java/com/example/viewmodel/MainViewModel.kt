package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ExtractedProductEntity
import com.example.data.ProcessedFileEntity
import com.example.data.SettingsRepository
import com.example.model.AppThemeMode
import com.example.model.DefaultSchemas
import com.example.model.DefaultValidationRules
import com.example.model.ExtractionSchema
import com.example.model.FieldValidationRule
import com.example.model.PdfProcessTask
import com.example.model.ProcessingStage
import com.example.model.ProductItem
import com.example.model.QueueExecutionState
import com.example.service.BackgroundExtractionService
import com.example.service.GeminiExtractor
import com.example.service.GoogleDriveUploader
import com.example.utils.ExcelCsvExporter
import com.example.utils.PdfChunkInfo
import com.example.utils.PdfChunker
import com.example.utils.RegexFieldValidator
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val settingsRepository = SettingsRepository(application)

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("MainViewModel", "Unhandled coroutine error in processScope: ${throwable.message}", throwable)
        _queueExecutionState.value = QueueExecutionState.IDLE
        _currentProcessingTaskId.value = null
    }

    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + coroutineExceptionHandler)

    private val _pdfTasks = MutableStateFlow<List<PdfProcessTask>>(emptyList())
    val pdfTasks: StateFlow<List<PdfProcessTask>> = _pdfTasks.asStateFlow()

    private val _queueExecutionState = MutableStateFlow(QueueExecutionState.IDLE)
    val queueExecutionState: StateFlow<QueueExecutionState> = _queueExecutionState.asStateFlow()

    private val _currentProcessingTaskId = MutableStateFlow<String?>(null)
    val currentProcessingTaskId: StateFlow<String?> = _currentProcessingTaskId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private var activeQueueJob: Job? = null

    val themeMode: StateFlow<AppThemeMode> = settingsRepository.themeModeFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AppThemeMode.SYSTEM
    )

    val geminiApiKey: StateFlow<String> = settingsRepository.geminiApiKeyFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )

    val driveOAuthToken: StateFlow<String> = settingsRepository.driveOAuthTokenFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )

    val driveFolderId: StateFlow<String> = settingsRepository.driveFolderIdFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), GoogleDriveUploader.FOLDER_ID
    )

    val driveAutoUpload: StateFlow<Boolean> = settingsRepository.driveAutoUploadFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    val backgroundProcessingEnabled: StateFlow<Boolean> = settingsRepository.backgroundProcessingFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    val validationRules: StateFlow<List<FieldValidationRule>> = settingsRepository.validationRulesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultValidationRules.getDefaultRules()
    )

    val allSchemas: StateFlow<List<ExtractionSchema>> = settingsRepository.allSchemasFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultSchemas.getAllPresets()
    )

    val activeSchemaId: StateFlow<String> = settingsRepository.activeSchemaIdFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultSchemas.ID_SUPERMARKET
    )

    val savedFiles = db.fileDao().getAllFiles().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allExtractedProducts = db.productDao().getAllProducts().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    init {
        viewModelScope.launch {
            try {
                db.fileDao().getAllFiles().collect { entities ->
                    if (_pdfTasks.value.isEmpty() && entities.isNotEmpty()) {
                        _pdfTasks.value = entities.map { entity ->
                            val savedProducts = try {
                                db.productDao().getProductsForFile(entity.id).map { p ->
                                    var customMap = emptyMap<String, String>()
                                    if (p.customFieldsJson.isNotBlank() && p.customFieldsJson != "{}") {
                                        try {
                                            customMap = Json.decodeFromString<Map<String, String>>(p.customFieldsJson)
                                        } catch (e: Exception) {
                                            // Ignore corrupt JSON
                                        }
                                    }

                                    ProductItem(
                                        Produit = p.produit,
                                        Brand = p.brand,
                                        Category = p.category,
                                        Prix = p.prix,
                                        Prix_Normal = p.prixNormal,
                                        Date_Promo = p.datePromo,
                                        unité = p.unite,
                                        SKU = p.sku,
                                        Barcode = p.barcode,
                                        customFields = customMap
                                    )
                                }
                            } catch (e: Exception) {
                                emptyList()
                            }
                            PdfProcessTask(
                                id = entity.id,
                                fileName = entity.fileName,
                                fileUri = Uri.EMPTY,
                                fileSizeByte = entity.fileSizeByte,
                                totalPages = entity.totalPages,
                                totalChunks = entity.totalChunks,
                                products = savedProducts,
                                stage = if (entity.errorMessage != null) ProcessingStage.Error(entity.errorMessage) else ProcessingStage.Completed,
                                progress = 1.0f,
                                xlsxFilePath = entity.xlsxFilePath,
                                csvFilePath = entity.csvFilePath,
                                driveXlsxUrl = entity.driveXlsxUrl,
                                driveCsvUrl = entity.driveCsvUrl,
                                errorMessage = entity.errorMessage
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading saved files from DB: ${e.message}", e)
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            try {
                settingsRepository.setThemeMode(mode)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error setting theme: ${e.message}")
            }
        }
    }

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setGeminiApiKey(key)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error saving Gemini API key: ${e.message}")
            }
        }
    }

    fun setDriveOAuthToken(token: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setDriveOAuthToken(token)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error saving Drive OAuth token: ${e.message}")
            }
        }
    }

    fun setDriveFolderId(folderId: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setDriveFolderId(folderId)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error saving Drive folder ID: ${e.message}")
            }
        }
    }

    fun setDriveAutoUpload(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setDriveAutoUpload(enabled)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error saving Drive auto-upload: ${e.message}")
            }
        }
    }

    fun setBackgroundProcessing(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setBackgroundProcessing(enabled)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error saving background processing toggle: ${e.message}")
            }
        }
    }

    fun saveValidationRules(rules: List<FieldValidationRule>) {
        viewModelScope.launch {
            try {
                settingsRepository.saveValidationRules(rules)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error saving validation rules: ${e.message}")
            }
        }
    }

    fun resetValidationRulesToDefault() {
        viewModelScope.launch {
            try {
                settingsRepository.resetValidationRulesToDefault()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error resetting validation rules: ${e.message}")
            }
        }
    }

    fun setActiveSchema(schemaId: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setActiveSchemaId(schemaId)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error setting active schema: ${e.message}")
            }
        }
    }

    fun saveCustomSchema(schema: ExtractionSchema) {
        viewModelScope.launch {
            try {
                settingsRepository.saveCustomSchema(schema)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error saving schema: ${e.message}")
            }
        }
    }

    fun deleteCustomSchema(schemaId: String) {
        viewModelScope.launch {
            try {
                settingsRepository.deleteCustomSchema(schemaId)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error deleting schema: ${e.message}")
            }
        }
    }

    fun resetSchemasToDefault() {
        viewModelScope.launch {
            try {
                settingsRepository.resetSchemasToDefault()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error resetting schemas: ${e.message}")
            }
        }
    }

    fun deleteSavedFile(fileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.fileDao().deleteById(fileId)
                db.productDao().deleteForFile(fileId)
                _pdfTasks.value = _pdfTasks.value.filter { it.id != fileId }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error deleting file from DB: ${e.message}")
            }
        }
    }

    fun clearAllSavedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.fileDao().deleteAll()
                db.productDao().deleteAll()
                _pdfTasks.value = emptyList()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error clearing saved files: ${e.message}")
            }
        }
    }

    suspend fun getProductsForFile(fileId: String): List<ExtractedProductEntity> = withContext(Dispatchers.IO) {
        try {
            db.productDao().getProductsForFile(fileId)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error fetching products for file $fileId: ${e.message}")
            emptyList()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    // ==========================================
    // QUEUE SYSTEM IMPLEMENTATION
    // ==========================================

    /**
     * Add selected PDF URIs to queue with currently active schema (or optional override)
     * and trigger sequential background execution.
     */
    fun addPdfUris(uris: List<Uri>, schemaIdOverride: String? = null) {
        if (uris.isEmpty()) return

        val context = getApplication<Application>()
        val currentActiveSchemaId = schemaIdOverride ?: activeSchemaId.value
        val currentSchema = allSchemas.value.find { it.id == currentActiveSchemaId } ?: DefaultSchemas.getDefaultSchema()

        val newTasks = mutableListOf<PdfProcessTask>()

        uris.forEach { uri ->
            try {
                val fileName = getFileNameFromUri(context, uri) ?: "Document_${System.currentTimeMillis()}.pdf"
                val fileSize = PdfChunker.getFileSize(context, uri)
                val taskId = UUID.randomUUID().toString()

                val task = PdfProcessTask(
                    id = taskId,
                    fileName = fileName,
                    fileUri = uri,
                    fileSizeByte = fileSize,
                    stage = ProcessingStage.Queued,
                    progress = 0f,
                    schemaId = currentSchema.id,
                    schemaName = currentSchema.name
                )
                newTasks.add(task)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error parsing URI $uri: ${e.message}")
            }
        }

        if (newTasks.isNotEmpty()) {
            _pdfTasks.value = _pdfTasks.value + newTasks

            // Start or resume queue if not paused
            if (_queueExecutionState.value != QueueExecutionState.PAUSED) {
                startOrResumeQueue()
            }
        }
    }

    fun assignSchemaToTask(taskId: String, schemaId: String) {
        val schema = allSchemas.value.find { it.id == schemaId } ?: return
        updateTask(taskId) {
            it.copy(
                schemaId = schema.id,
                schemaName = schema.name
            )
        }
    }

    /**
     * Start / Resume sequential processing of all pending tasks in the queue.
     */
    fun startOrResumeQueue() {
        if (activeQueueJob?.isActive == true) return

        _queueExecutionState.value = QueueExecutionState.PROCESSING

        activeQueueJob = processScope.launch {
            try {
                runSequentialQueueProcessor()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Queue processor encountered unexpected error: ${e.message}", e)
                _queueExecutionState.value = QueueExecutionState.IDLE
                _currentProcessingTaskId.value = null
            }
        }
    }

    /**
     * Pauses the sequential queue. Active task will finish or pause, and remaining tasks wait in queue.
     */
    fun pauseQueue() {
        _queueExecutionState.value = QueueExecutionState.PAUSED
        activeQueueJob?.cancel()
        activeQueueJob = null
        _currentProcessingTaskId.value = null

        val context = getApplication<Application>()
        if (backgroundProcessingEnabled.value) {
            BackgroundExtractionService.updateProgress(
                context = context,
                title = "Queue Paused",
                text = "Background sequential extraction paused",
                progress = 0,
                maxProgress = 100
            )
        }
    }

    /**
     * Stops the queue processing completely.
     */
    fun stopQueue() {
        _queueExecutionState.value = QueueExecutionState.IDLE
        activeQueueJob?.cancel()
        activeQueueJob = null
        _currentProcessingTaskId.value = null

        val context = getApplication<Application>()
        if (backgroundProcessingEnabled.value) {
            BackgroundExtractionService.stopService(context)
        }
    }

    fun moveTaskUp(taskId: String) {
        val list = _pdfTasks.value.toMutableList()
        val index = list.indexOfFirst { it.id == taskId }
        if (index > 0) {
            val item = list.removeAt(index)
            list.add(index - 1, item)
            _pdfTasks.value = list
        }
    }

    fun moveTaskDown(taskId: String) {
        val list = _pdfTasks.value.toMutableList()
        val index = list.indexOfFirst { it.id == taskId }
        if (index in 0 until list.size - 1) {
            val item = list.removeAt(index)
            list.add(index + 1, item)
            _pdfTasks.value = list
        }
    }

    fun moveTaskToTop(taskId: String) {
        val list = _pdfTasks.value.toMutableList()
        val index = list.indexOfFirst { it.id == taskId }
        if (index > 0) {
            val item = list.removeAt(index)
            list.add(0, item)
            _pdfTasks.value = list
        }
    }

    fun retryTask(taskId: String) {
        updateTask(taskId) {
            it.copy(
                stage = ProcessingStage.Queued,
                errorMessage = null,
                progress = 0f
            )
        }
        startOrResumeQueue()
    }

    fun retryAllFailed() {
        _pdfTasks.value = _pdfTasks.value.map {
            if (it.stage is ProcessingStage.Error) {
                it.copy(stage = ProcessingStage.Queued, errorMessage = null, progress = 0f)
            } else {
                it
            }
        }
        startOrResumeQueue()
    }

    fun clearCompletedTasks() {
        _pdfTasks.value = _pdfTasks.value.filter { it.stage !is ProcessingStage.Completed }
    }

    /**
     * Sequential background queue worker. Executes one PDF task at a time in queue order.
     * Continues smoothly through failures without terminating the entire queue.
     */
    private suspend fun runSequentialQueueProcessor() = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val useBgService = backgroundProcessingEnabled.value

        while (_queueExecutionState.value == QueueExecutionState.PROCESSING) {
            val tasksSnapshot = _pdfTasks.value
            val nextTask = tasksSnapshot.firstOrNull {
                it.stage is ProcessingStage.Queued
            }

            if (nextTask == null) {
                // Queue finished!
                _queueExecutionState.value = QueueExecutionState.IDLE
                _currentProcessingTaskId.value = null

                if (useBgService) {
                    val completedCount = _pdfTasks.value.count { it.stage is ProcessingStage.Completed }
                    val errorCount = _pdfTasks.value.count { it.stage is ProcessingStage.Error }
                    val text = if (errorCount > 0) {
                        "$completedCount completed, $errorCount encountered issues."
                    } else {
                        "All $completedCount queue files processed & ready for export."
                    }
                    BackgroundExtractionService.notifyCompleted(
                        context = context,
                        title = "Queue Processing Complete",
                        text = text
                    )
                }
                break
            }

            val totalInQueue = _pdfTasks.value.size
            val currentTaskIndex = _pdfTasks.value.indexOfFirst { it.id == nextTask.id }
            _currentProcessingTaskId.value = nextTask.id

            val schema = allSchemas.value.find { it.id == nextTask.schemaId }
                ?: allSchemas.value.find { it.id == activeSchemaId.value }
                ?: DefaultSchemas.getDefaultSchema()

            if (useBgService) {
                BackgroundExtractionService.startService(
                    context = context,
                    title = "Queue [${currentTaskIndex + 1}/$totalInQueue]: ${nextTask.fileName}",
                    text = "Extracting with '${schema.name}'...",
                    progress = ((currentTaskIndex.toFloat() / totalInQueue.coerceAtLeast(1)) * 100).toInt(),
                    maxProgress = 100
                )
            }

            try {
                processSinglePdfTask(nextTask.id, currentTaskIndex, totalInQueue, schema)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Fatal error on task ${nextTask.id}: ${e.message}", e)
                updateTask(nextTask.id) {
                    it.copy(
                        stage = ProcessingStage.Error(e.message ?: "Processing failed"),
                        errorMessage = e.message ?: "Processing failed",
                        progress = 0f
                    )
                }
            }
        }
    }

    private suspend fun processSinglePdfTask(
        taskId: String,
        batchIndex: Int = 0,
        batchTotal: Int = 1,
        schema: ExtractionSchema = DefaultSchemas.getDefaultSchema()
    ) = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val useBgService = backgroundProcessingEnabled.value
        val currentTask = _pdfTasks.value.find { it.id == taskId } ?: return@withContext
        var chunks: List<PdfChunkInfo>? = null

        fun updateBgProgress(stageText: String, taskProgressFrac: Float) {
            if (!useBgService) return
            val overallPercent = if (batchTotal > 0) {
                (((batchIndex.toFloat() + taskProgressFrac.coerceIn(0f, 1f)) / batchTotal) * 100).toInt()
            } else {
                (taskProgressFrac * 100).toInt()
            }
            BackgroundExtractionService.updateProgress(
                context = context,
                title = "Queue [${batchIndex + 1}/$batchTotal]: ${currentTask.fileName}",
                text = stageText,
                progress = overallPercent,
                maxProgress = 100
            )
        }

        try {
            // Helper for appending step logs
            fun addLog(log: String) {
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                updateTask(taskId) { current ->
                    current.copy(
                        stepLogs = current.stepLogs + "[$timeStr] $log",
                        currentStepDetail = log
                    )
                }
            }

            // Step 1: Analyzing PDF structure & metadata
            addLog("Step 1/6: Analyzing PDF metadata & document structure...")
            updateTask(taskId) {
                it.copy(
                    stage = ProcessingStage.Analyzing,
                    errorMessage = null,
                    progress = 0.05f
                )
            }
            updateBgProgress("Step 1/6: Analyzing PDF structure...", 0.05f)

            // Prepare Chunks
            chunks = PdfChunker.prepareChunks(context, currentTask.fileUri, currentTask.fileName)
            val totalChunks = chunks.size
            val totalPages = PdfChunker.getPageCount(context, currentTask.fileUri)

            // Step 2: Document Partitioning & Streaming
            addLog("Step 2/6: Partitioned into $totalChunks chunk(s) across $totalPages pages.")
            updateTask(taskId) {
                it.copy(
                    totalPages = totalPages,
                    totalChunks = totalChunks,
                    stage = ProcessingStage.Splitting(1, totalChunks),
                    progress = 0.15f
                )
            }
            updateBgProgress("Step 2/6: Partitioned into $totalChunks chunk(s)...", 0.15f)

            // Step 3: Extracting with Gemini API using Schema
            val apiKey = geminiApiKey.value
            val chunkProductsList = mutableListOf<List<ProductItem>>()
            var cumulativeExtractedCount = 0

            addLog("Step 3/6: Starting Gemini AI extraction using schema '${schema.name}'...")

            for ((idx, chunk) in chunks.withIndex()) {
                val chunkNum = idx + 1
                val progressVal = 0.15f + ((chunkNum.toFloat() / totalChunks) * 0.45f)

                updateTask(taskId) {
                    it.copy(
                        currentChunk = chunkNum,
                        stage = ProcessingStage.Extracting(chunkNum, totalChunks, cumulativeExtractedCount),
                        progress = progressVal,
                        currentStepDetail = "Extracting chunk $chunkNum/$totalChunks with Gemini AI ($cumulativeExtractedCount items extracted so far)..."
                    )
                }
                updateBgProgress("Step 3/6: AI extracting chunk $chunkNum of $totalChunks (${schema.name})...", progressVal)

                try {
                    val extracted = GeminiExtractor.extractFromChunk(chunk, apiKey, schema)
                    chunkProductsList.add(extracted)
                    cumulativeExtractedCount += extracted.size
                    addLog("Extracted ${extracted.size} items from chunk $chunkNum/$totalChunks (Total: $cumulativeExtractedCount items).")
                    
                    updateTask(taskId) {
                        it.copy(
                            stage = ProcessingStage.Extracting(chunkNum, totalChunks, cumulativeExtractedCount)
                        )
                    }
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Chunk $chunkNum extraction notice: ${e.message}")
                    addLog("Warning in chunk $chunkNum: ${e.message}")
                    if (chunkProductsList.isEmpty() && idx == chunks.size - 1) {
                        throw e
                    }
                } finally {
                    chunk.cleanup()
                    System.gc()
                }
            }

            val rawMergedProducts = GeminiExtractor.mergeChunkResults(chunkProductsList)
            addLog("Step 3/6 Complete: Total ${rawMergedProducts.size} raw products retrieved.")

            // Step 4: Apply custom regex field validation & cleaning rules
            addLog("Step 4/6: Validating & normalizing ${rawMergedProducts.size} products against schema regex rules...")
            updateTask(taskId) {
                it.copy(
                    stage = ProcessingStage.Validating(rawMergedProducts.size),
                    progress = 0.65f
                )
            }
            updateBgProgress("Step 4/6: Validating ${rawMergedProducts.size} products...", 0.65f)

            val currentRules = validationRules.value
            val (validatedProducts, _) = RegexFieldValidator.processExtractedProducts(
                products = rawMergedProducts,
                rules = currentRules
            )
            addLog("Step 4/6 Complete: ${validatedProducts.size} products validated and cleaned.")

            // Step 5: File Generation (.xlsx & .csv) with Schema Headers
            addLog("Step 5/6: Compiling Excel (.xlsx) workbook and UTF-8 CSV datasets...")
            updateTask(taskId) {
                it.copy(
                    stage = ProcessingStage.GeneratingFiles(validatedProducts.size),
                    progress = 0.75f,
                    products = validatedProducts
                )
            }
            updateBgProgress("Step 5/6: Generating Excel & CSV (${validatedProducts.size} items)...", 0.75f)

            val exportResult = ExcelCsvExporter.exportProducts(
                context = context,
                originalPdfName = currentTask.fileName,
                products = validatedProducts,
                schema = schema
            )

            addLog("Step 5/6 Complete: Excel (${exportResult.xlsxFile.name}) & CSV files compiled.")

            updateTask(taskId) {
                it.copy(
                    xlsxFilePath = exportResult.xlsxFile.absolutePath,
                    csvFilePath = exportResult.csvFile.absolutePath,
                    progress = 0.85f
                )
            }

            // Step 6: Google Drive Upload & Cloud Storage Sync
            val oAuthToken = driveOAuthToken.value
            val isAutoUploadEnabled = driveAutoUpload.value
            var driveXlsxUrl: String? = null
            var driveCsvUrl: String? = null
            var driveError: String? = null

            if (isAutoUploadEnabled && oAuthToken.isNotBlank()) {
                addLog("Step 6/6: Uploading .xlsx and .csv files to Google Drive...")
                updateTask(taskId) {
                    it.copy(
                        stage = ProcessingStage.UploadingDrive("Uploading to Google Drive..."),
                        progress = 0.90f
                    )
                }
                updateBgProgress("Step 6/6: Uploading to Google Drive...", 0.90f)

                try {
                    val folder = driveFolderId.value
                    val xlsxUpload = GoogleDriveUploader.uploadFile(
                        file = exportResult.xlsxFile,
                        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        oauthToken = oAuthToken,
                        folderId = folder
                    )
                    driveXlsxUrl = xlsxUpload.webViewLink

                    val csvUpload = GoogleDriveUploader.uploadFile(
                        file = exportResult.csvFile,
                        mimeType = "text/csv",
                        oauthToken = oAuthToken,
                        folderId = folder
                    )
                    driveCsvUrl = csvUpload.webViewLink
                    addLog("Google Drive upload successful.")
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Drive auto-upload failed: ${e.message}")
                    driveError = e.message
                    addLog("Google Drive upload skipped/notice: ${e.message}")
                }
            } else if (!isAutoUploadEnabled) {
                driveError = null
                addLog("Step 6/6: Google Drive auto-upload disabled. Preserving local files.")
            } else {
                driveError = "Drive upload skipped: OAuth token not configured in Settings."
                addLog("Step 6/6: Drive upload skipped (no OAuth token).")
            }

            // Stage Complete: All 6 steps finished!
            addLog("Processing Complete! ${validatedProducts.size} products extracted.")
            updateTask(taskId) {
                it.copy(
                    stage = ProcessingStage.Completed,
                    progress = 1.0f,
                    driveXlsxUrl = driveXlsxUrl,
                    driveCsvUrl = driveCsvUrl,
                    driveError = driveError,
                    currentStepDetail = "All 6 extraction steps completed successfully."
                )
            }
            updateBgProgress("Completed (${validatedProducts.size} items extracted)", 1.0f)

            // Save to Room DB
            val finalTask = _pdfTasks.value.find { it.id == taskId }
            if (finalTask != null) {
                try {
                    saveToDatabase(finalTask)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to persist task to database: ${e.message}")
                }
            }

        } catch (oom: OutOfMemoryError) {
            System.gc()
            val msg = "Out of Memory: File exceeds available device memory."
            Log.e("MainViewModel", msg, oom)
            updateTask(taskId) {
                it.copy(
                    stage = ProcessingStage.Error(msg),
                    errorMessage = msg,
                    progress = 0f
                )
            }
            updateBgProgress("Error: $msg", 1.0f)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error processing PDF $taskId: ${e.message}", e)
            val msg = e.message ?: "An unexpected error occurred during processing."
            updateTask(taskId) {
                it.copy(
                    stage = ProcessingStage.Error(msg),
                    errorMessage = msg,
                    progress = 0f
                )
            }
            updateBgProgress("Error: $msg", 1.0f)
        } finally {
            chunks?.forEach { it.cleanup() }
        }
    }

    private suspend fun saveToDatabase(task: PdfProcessTask) {
        try {
            val entity = ProcessedFileEntity(
                id = task.id,
                fileName = task.fileName,
                fileSizeByte = task.fileSizeByte,
                totalPages = task.totalPages,
                totalChunks = task.totalChunks,
                productCount = task.products.size,
                stageLabel = task.stage.label(),
                xlsxFilePath = task.xlsxFilePath,
                csvFilePath = task.csvFilePath,
                driveXlsxUrl = task.driveXlsxUrl,
                driveCsvUrl = task.driveCsvUrl,
                errorMessage = task.errorMessage
            )
            db.fileDao().insertOrUpdate(entity)

            val productEntities = task.products.map { p ->
                ExtractedProductEntity(
                    fileId = task.id,
                    sourceFileName = task.fileName,
                    produit = p.Produit,
                    brand = p.Brand,
                    category = p.Category,
                    prix = p.Prix,
                    prixNormal = p.Prix_Normal,
                    datePromo = p.Date_Promo,
                    unite = p.unité,
                    sku = p.SKU,
                    barcode = p.Barcode,
                    customFieldsJson = if (p.customFields.isNotEmpty()) {
                        try {
                            Json.encodeToString(p.customFields)
                        } catch (e: Exception) {
                            "{}"
                        }
                    } else {
                        "{}"
                    }
                )
            }
            db.productDao().insertAll(productEntities)
        } catch (e: Exception) {
            Log.e("MainViewModel", "saveToDatabase error: ${e.message}", e)
        }
    }

    private fun updateTask(taskId: String, block: (PdfProcessTask) -> PdfProcessTask) {
        _pdfTasks.value = _pdfTasks.value.map {
            if (it.id == taskId) block(it) else it
        }
    }

    fun removeTask(taskId: String) {
        _pdfTasks.value = _pdfTasks.value.filter { it.id != taskId }
        viewModelScope.launch {
            try {
                db.fileDao().deleteById(taskId)
                db.productDao().deleteForFile(taskId)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error removing task from DB: ${e.message}")
            }
        }
    }

    fun batchDeleteTasks(taskIds: Set<String>) {
        if (taskIds.isEmpty()) return
        _pdfTasks.value = _pdfTasks.value.filter { it.id !in taskIds }
        viewModelScope.launch {
            try {
                taskIds.forEach { id ->
                    db.fileDao().deleteById(id)
                    db.productDao().deleteForFile(id)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error batch deleting tasks from DB: ${e.message}")
            }
        }
    }

    fun bulkRenameTasks(renamedMap: Map<String, String>) {
        if (renamedMap.isEmpty()) return
        val context = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentTasks = _pdfTasks.value
                val updatedTasks = currentTasks.map { task ->
                    val newName = renamedMap[task.id]
                    if (newName != null && newName.isNotBlank() && newName != task.fileName) {
                        var newXlsxPath = task.xlsxFilePath
                        var newCsvPath = task.csvFilePath

                        if (task.products.isNotEmpty()) {
                            try {
                                val schema = allSchemas.value.find { it.id == task.schemaId }
                                val exportResult = ExcelCsvExporter.exportProducts(
                                    context = context,
                                    originalPdfName = newName,
                                    products = task.products,
                                    schema = schema
                                )
                                newXlsxPath = exportResult.xlsxFile.absolutePath
                                newCsvPath = exportResult.csvFile.absolutePath
                            } catch (e: Exception) {
                                Log.e("MainViewModel", "Failed to re-export after rename: ${e.message}")
                            }
                        }

                        val updatedTask = task.copy(
                            fileName = newName,
                            xlsxFilePath = newXlsxPath,
                            csvFilePath = newCsvPath
                        )
                        saveToDatabase(updatedTask)
                        updatedTask
                    } else {
                        task
                    }
                }

                withContext(Dispatchers.Main) {
                    _pdfTasks.value = updatedTasks
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error bulk renaming tasks: ${e.message}")
            }
        }
    }

    fun batchUploadToDrive(taskIds: Set<String>, onResult: ((Int, Int) -> Unit)? = null) {
        val oAuthToken = driveOAuthToken.value
        val folder = driveFolderId.value

        val context = getApplication<Application>()
        val useBgService = backgroundProcessingEnabled.value

        if (oAuthToken.isBlank()) {
            taskIds.forEach { id ->
                updateTask(id) {
                    it.copy(driveError = "OAuth token not set in Settings.")
                }
            }
            onResult?.invoke(0, taskIds.size)
            return
        }

        processScope.launch(Dispatchers.IO) {
            var successCount = 0
            var failCount = 0
            val totalUploads = taskIds.size

            if (useBgService && totalUploads > 0) {
                BackgroundExtractionService.startService(
                    context = context,
                    title = "Uploading to Google Drive",
                    text = "Preparing file upload in background...",
                    progress = 0,
                    maxProgress = 100
                )
            }

            for ((idx, id) in taskIds.withIndex()) {
                val task = _pdfTasks.value.find { it.id == id } ?: continue
                if (task.xlsxFilePath == null && task.csvFilePath == null) continue

                if (useBgService) {
                    BackgroundExtractionService.updateProgress(
                        context = context,
                        title = "Uploading (${idx + 1}/$totalUploads): ${task.fileName}",
                        text = "Uploading to Google Drive...",
                        progress = ((idx.toFloat() / totalUploads) * 100).toInt(),
                        maxProgress = 100
                    )
                }

                updateTask(id) {
                    it.copy(stage = ProcessingStage.UploadingDrive("Uploading to Google Drive..."))
                }

                var driveXlsxUrl: String? = task.driveXlsxUrl
                var driveCsvUrl: String? = task.driveCsvUrl
                var driveError: String? = null

                try {
                    if (task.xlsxFilePath != null) {
                        val xlsxFile = java.io.File(task.xlsxFilePath)
                        if (xlsxFile.exists()) {
                            val upload = GoogleDriveUploader.uploadFile(
                                file = xlsxFile,
                                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                oauthToken = oAuthToken,
                                folderId = folder
                            )
                            driveXlsxUrl = upload.webViewLink
                        }
                    }

                    if (task.csvFilePath != null) {
                        val csvFile = java.io.File(task.csvFilePath)
                        if (csvFile.exists()) {
                            val upload = GoogleDriveUploader.uploadFile(
                                file = csvFile,
                                mimeType = "text/csv",
                                oauthToken = oAuthToken,
                                folderId = folder
                            )
                            driveCsvUrl = upload.webViewLink
                        }
                    }
                    successCount++
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Batch Drive upload failed for ${task.fileName}: ${e.message}")
                    driveError = e.message
                    failCount++
                }

                updateTask(id) {
                    it.copy(
                        stage = ProcessingStage.Completed,
                        driveXlsxUrl = driveXlsxUrl,
                        driveCsvUrl = driveCsvUrl,
                        driveError = driveError
                    )
                }

                val updated = _pdfTasks.value.find { it.id == id }
                if (updated != null) {
                    saveToDatabase(updated)
                }
            }

            if (useBgService) {
                BackgroundExtractionService.notifyCompleted(
                    context = context,
                    title = "Drive Upload Complete",
                    text = "Uploaded $successCount file(s) to Google Drive."
                )
            }

            withContext(Dispatchers.Main) {
                onResult?.invoke(successCount, failCount)
            }
        }
    }

    fun clearAllTasks() {
        _pdfTasks.value = emptyList()
    }

    private fun getFileNameFromUri(context: Application, uri: Uri): String? {
        var name: String? = null
        try {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            }
            if (name == null) {
                name = uri.path?.let { path ->
                    val cut = path.lastIndexOf('/')
                    if (cut != -1) path.substring(cut + 1) else path
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to retrieve filename from uri: ${e.message}")
        }
        return name
    }
}
