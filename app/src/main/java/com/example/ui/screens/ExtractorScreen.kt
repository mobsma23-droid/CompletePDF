package com.example.ui.screens

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExtractionSchema
import com.example.model.PdfProcessTask
import com.example.model.ProcessingStage
import com.example.model.QueueExecutionState
import com.example.ui.components.BulkRenameDialog
import com.example.ui.components.PreviewDataDialog
import com.example.ui.components.SchemaManagerDialog
import com.example.ui.components.SchemaPickerSheet
import com.example.ui.components.TaskProgressCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractorScreen(
    tasks: List<PdfProcessTask>,
    queueState: QueueExecutionState = QueueExecutionState.IDLE,
    activeSchemaId: String = "",
    allSchemas: List<ExtractionSchema> = emptyList(),
    onSelectFiles: (List<Uri>) -> Unit,
    onRemoveTask: (String) -> Unit,
    onClearAll: () -> Unit,
    onStartQueue: () -> Unit,
    onPauseQueue: () -> Unit,
    onRetryTask: ((String) -> Unit)? = null,
    onRetryAllFailed: (() -> Unit)? = null,
    onClearCompleted: (() -> Unit)? = null,
    onMoveTaskUp: ((String) -> Unit)? = null,
    onMoveTaskDown: ((String) -> Unit)? = null,
    onAssignTaskSchema: ((String, String) -> Unit)? = null,
    onSetActiveSchema: (String) -> Unit = {},
    onSaveSchema: (ExtractionSchema) -> Unit = {},
    onDeleteSchema: (String) -> Unit = {},
    onResetSchemasDefaults: () -> Unit = {},
    onBatchDelete: ((Set<String>) -> Unit)? = null,
    onBatchUploadToDrive: ((Set<String>) -> Unit)? = null,
    onBulkRename: ((Map<String, String>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var previewTask by remember { mutableStateOf<PdfProcessTask?>(null) }
    var showBulkRenameDialog by remember { mutableStateOf(false) }
    var tasksToRename by remember { mutableStateOf<List<PdfProcessTask>>(emptyList()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTaskIds by remember { mutableStateOf(setOf<String>()) }

    var showSchemaPickerSheet by remember { mutableStateOf(false) }
    var showFullSchemaManagerDialog by remember { mutableStateOf(false) }
    var targetTaskIdForSchemaChange by remember { mutableStateOf<String?>(null) }

    val activeSchema = allSchemas.find { it.id == activeSchemaId } ?: allSchemas.firstOrNull()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            onSelectFiles(uris)
        }
    }

    // Keep selected IDs valid if items are removed
    val currentTaskIds = remember(tasks) { tasks.map { it.id }.toSet() }
    val validSelectedIds = selectedTaskIds.intersect(currentTaskIds)

    val completedCount = tasks.count { it.stage is ProcessingStage.Completed }
    val errorCount = tasks.count { it.stage is ProcessingStage.Error }
    val queuedCount = tasks.count { it.stage is ProcessingStage.Queued }
    val isActivelyProcessing = queueState == QueueExecutionState.PROCESSING

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Active Schema Selector Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showSchemaPickerSheet = true }
                    .testTag("banner_active_schema")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schema,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Active Schema:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeSchema?.name ?: "Supermarket Flyer",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "${activeSchema?.fields?.size ?: 7} extraction fields (${activeSchema?.fields?.take(4)?.joinToString { it.name } ?: "Produit, Prix, Brand"}...)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(
                        onClick = { showFullSchemaManagerDialog = true },
                        modifier = Modifier.size(32.dp).testTag("btn_configure_schemas")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Configure Schemas", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Background Active Indicator Banner
            if (isActivelyProcessing) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sequential Queue Processing in Background Service",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Main Primary Actions Bar (Select Multiple PDFs & Selection Tools)
            if (!isSelectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("select_pdf_button"),
                        contentPadding = ButtonDefaults.ContentPadding
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add PDFs",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add PDF Files", fontWeight = FontWeight.Bold)
                    }

                    if (tasks.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                isSelectionMode = true
                                selectedTaskIds = emptySet()
                            },
                            modifier = Modifier.testTag("enable_multi_select_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = "Select Multiple",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select")
                        }

                        IconButton(
                            onClick = onClearAll,
                            modifier = Modifier.testTag("clear_queue_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Queue",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            } else {
                // Multi-Select Contextual Action Bar
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("multi_select_bar"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    isSelectionMode = false
                                    selectedTaskIds = emptySet()
                                },
                                modifier = Modifier.testTag("cancel_multi_select_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Exit Selection Mode"
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${validSelectedIds.size} Selected",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Select All Toggle
                            TextButton(
                                onClick = {
                                    selectedTaskIds = if (validSelectedIds.size == tasks.size) {
                                        emptySet()
                                    } else {
                                        tasks.map { it.id }.toSet()
                                    }
                                },
                                modifier = Modifier.testTag("select_all_toggle_button")
                            ) {
                                Text(if (validSelectedIds.size == tasks.size) "Deselect" else "Select All", fontSize = 12.sp)
                            }

                            // Batch Rename
                            IconButton(
                                onClick = {
                                    val selected = tasks.filter { it.id in validSelectedIds }
                                    if (selected.isNotEmpty()) {
                                        tasksToRename = selected
                                        showBulkRenameDialog = true
                                    }
                                },
                                enabled = validSelectedIds.isNotEmpty(),
                                modifier = Modifier.testTag("batch_rename_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DriveFileRenameOutline,
                                    contentDescription = "Bulk Rename Selected",
                                    tint = if (validSelectedIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }

                            // Batch Upload to Google Drive
                            if (onBatchUploadToDrive != null) {
                                IconButton(
                                    onClick = {
                                        if (validSelectedIds.isNotEmpty()) {
                                            onBatchUploadToDrive(validSelectedIds)
                                            Toast.makeText(context, "Batch uploading ${validSelectedIds.size} files to Drive...", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = validSelectedIds.isNotEmpty(),
                                    modifier = Modifier.testTag("batch_drive_upload_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = "Upload Selected to Drive",
                                        tint = if (validSelectedIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            // Batch Delete
                            IconButton(
                                onClick = {
                                    if (validSelectedIds.isNotEmpty()) {
                                        if (onBatchDelete != null) {
                                            onBatchDelete(validSelectedIds)
                                        } else {
                                            validSelectedIds.forEach { onRemoveTask(it) }
                                        }
                                        selectedTaskIds = emptySet()
                                        isSelectionMode = false
                                    }
                                },
                                enabled = validSelectedIds.isNotEmpty(),
                                modifier = Modifier.testTag("batch_delete_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Selected",
                                    tint = if (validSelectedIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Queue Controls & Stats Bar
            if (tasks.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_queue_controls"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Queue Status:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = when (queueState) {
                                            QueueExecutionState.PROCESSING -> MaterialTheme.colorScheme.primaryContainer
                                            QueueExecutionState.PAUSED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            text = when (queueState) {
                                                QueueExecutionState.PROCESSING -> "Processing sequentially"
                                                QueueExecutionState.PAUSED -> "Paused"
                                                QueueExecutionState.IDLE -> if (queuedCount > 0) "Ready to start" else "Idle"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = when (queueState) {
                                                QueueExecutionState.PROCESSING -> MaterialTheme.colorScheme.primary
                                                QueueExecutionState.PAUSED -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${tasks.size} total • $completedCount completed • $queuedCount queued${if (errorCount > 0) " • $errorCount failed" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }

                            // Control buttons: Start/Resume vs Pause
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isActivelyProcessing) {
                                    FilledTonalButton(
                                        onClick = onPauseQueue,
                                        modifier = Modifier.height(34.dp).testTag("btn_pause_queue"),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pause", fontSize = 12.sp)
                                    }
                                } else if (queuedCount > 0 || errorCount > 0) {
                                    Button(
                                        onClick = onStartQueue,
                                        modifier = Modifier.height(34.dp).testTag("btn_start_queue"),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (queueState == QueueExecutionState.PAUSED) "Resume Queue" else "Start Queue", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Secondary actions: Clear completed & Retry failed
                        if ((completedCount > 0 && onClearCompleted != null) || (errorCount > 0 && onRetryAllFailed != null)) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (errorCount > 0 && onRetryAllFailed != null) {
                                    TextButton(
                                        onClick = onRetryAllFailed,
                                        modifier = Modifier.height(30.dp).testTag("btn_retry_all_failed"),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry Failed ($errorCount)", fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                if (completedCount > 0 && onClearCompleted != null) {
                                    TextButton(
                                        onClick = onClearCompleted,
                                        modifier = Modifier.height(30.dp).testTag("btn_clear_completed"),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Clear Completed ($completedCount)", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Task List or Empty State
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "No Files Selected",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No PDF Files in Queue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Select multiple catalog PDFs to queue them for background sequential processing using your configured extraction schemas.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select PDF Files")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                        val isSelected = task.id in validSelectedIds

                        TaskProgressCard(
                            task = task,
                            queueIndex = index,
                            totalQueueCount = tasks.size,
                            onRemove = { onRemoveTask(task.id) },
                            onRetry = if (onRetryTask != null) { { onRetryTask(task.id) } } else null,
                            onPreview = { previewTask = task },
                            onMoveUp = if (index > 0 && onMoveTaskUp != null) { { onMoveTaskUp(task.id) } } else null,
                            onMoveDown = if (index < tasks.size - 1 && onMoveTaskDown != null) { { onMoveTaskDown(task.id) } } else null,
                            onChangeSchema = {
                                targetTaskIdForSchemaChange = task.id
                                showSchemaPickerSheet = true
                            },
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onToggleSelect = {
                                selectedTaskIds = if (isSelected) {
                                    selectedTaskIds - task.id
                                } else {
                                    selectedTaskIds + task.id
                                }
                            },
                            onLongClick = {
                                isSelectionMode = true
                                selectedTaskIds = selectedTaskIds + task.id
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal Table Preview Dialog
    if (previewTask != null) {
        PreviewDataDialog(
            task = previewTask!!,
            onDismiss = { previewTask = null }
        )
    }

    // Modal Bulk Rename Dialog
    if (showBulkRenameDialog && tasksToRename.isNotEmpty()) {
        BulkRenameDialog(
            tasks = tasksToRename,
            onDismiss = {
                showBulkRenameDialog = false
                tasksToRename = emptyList()
            },
            onApplyRename = { renamedMap ->
                onBulkRename?.invoke(renamedMap)
                Toast.makeText(context, "Renamed ${renamedMap.size} file(s)", Toast.LENGTH_SHORT).show()
                showBulkRenameDialog = false
                tasksToRename = emptyList()
                if (isSelectionMode) {
                    isSelectionMode = false
                    selectedTaskIds = emptySet()
                }
            }
        )
    }

    // Schema Picker Bottom Sheet
    if (showSchemaPickerSheet) {
        val currentTargetTaskId = targetTaskIdForSchemaChange
        val currentSchemaId = if (currentTargetTaskId != null) {
            tasks.find { it.id == currentTargetTaskId }?.schemaId ?: activeSchemaId
        } else {
            activeSchemaId
        }

        SchemaPickerSheet(
            schemas = allSchemas,
            activeSchemaId = currentSchemaId,
            title = if (currentTargetTaskId != null) "Change Task Extraction Schema" else "Select Default Extraction Schema",
            subtitle = if (currentTargetTaskId != null) "Choose which schema Gemini AI will use for this catalog" else "Set default schema for all newly added PDF catalogs",
            onSelectSchema = { schemaId ->
                if (currentTargetTaskId != null && onAssignTaskSchema != null) {
                    onAssignTaskSchema(currentTargetTaskId, schemaId)
                    Toast.makeText(context, "Updated task schema", Toast.LENGTH_SHORT).show()
                } else {
                    onSetActiveSchema(schemaId)
                    Toast.makeText(context, "Active schema updated", Toast.LENGTH_SHORT).show()
                }
                targetTaskIdForSchemaChange = null
            },
            onOpenManager = {
                showFullSchemaManagerDialog = true
            },
            onDismiss = {
                showSchemaPickerSheet = false
                targetTaskIdForSchemaChange = null
            }
        )
    }

    // Full Schema Manager & Custom Schema Creator Dialog
    if (showFullSchemaManagerDialog) {
        SchemaManagerDialog(
            schemas = allSchemas,
            activeSchemaId = activeSchemaId,
            onSetActiveSchema = onSetActiveSchema,
            onSaveSchema = onSaveSchema,
            onDeleteSchema = onDeleteSchema,
            onResetDefaults = onResetSchemasDefaults,
            onDismiss = { showFullSchemaManagerDialog = false }
        )
    }
}
