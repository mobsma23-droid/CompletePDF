package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.data.ExtractedProductEntity
import com.example.data.ProcessedFileEntity
import com.example.model.PdfProcessTask
import com.example.model.ProcessingStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HistoryFilter {
    ALL,
    COMPLETED,
    DRIVE_SYNCED,
    ERRORS
}

enum class HistorySort {
    NEWEST,
    OLDEST,
    MOST_PRODUCTS,
    LARGEST_SIZE
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatusDashboardScreen(
    savedFiles: List<ProcessedFileEntity>,
    activeTasks: List<PdfProcessTask>,
    isProcessingBatch: Boolean,
    onDeleteFile: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    onLoadProductsForFile: suspend (String) -> List<ExtractedProductEntity>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var selectedSort by remember { mutableStateOf(HistorySort.NEWEST) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var inspectingFile by remember { mutableStateOf<ProcessedFileEntity?>(null) }
    var fileProducts by remember { mutableStateOf<List<ExtractedProductEntity>>(emptyList()) }
    var isLoadingProducts by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Aggregate statistics from Room
    val totalFiles = savedFiles.size
    val totalProducts = remember(savedFiles) { savedFiles.sumOf { it.productCount } }
    val completedCount = remember(savedFiles) {
        savedFiles.count { it.stageLabel.contains("Completed", ignoreCase = true) || it.productCount > 0 }
    }
    val errorCount = remember(savedFiles) {
        savedFiles.count { it.errorMessage != null && it.errorMessage.isNotBlank() }
    }
    val driveSyncedCount = remember(savedFiles) {
        savedFiles.count { !it.driveXlsxUrl.isNullOrBlank() || !it.driveCsvUrl.isNullOrBlank() }
    }
    val totalBytesProcessed = remember(savedFiles) { savedFiles.sumOf { it.fileSizeByte } }
    val successRate = if (totalFiles > 0) ((completedCount.toFloat() / totalFiles) * 100).toInt() else 100

    // Filter and Sort List
    val filteredFiles by remember(savedFiles, searchQuery, selectedFilter, selectedSort) {
        derivedStateOf {
            var list = savedFiles

            if (searchQuery.isNotBlank()) {
                list = list.filter {
                    it.fileName.contains(searchQuery, ignoreCase = true) ||
                    it.stageLabel.contains(searchQuery, ignoreCase = true)
                }
            }

            list = when (selectedFilter) {
                HistoryFilter.ALL -> list
                HistoryFilter.COMPLETED -> list.filter { it.errorMessage.isNullOrBlank() }
                HistoryFilter.DRIVE_SYNCED -> list.filter { !it.driveXlsxUrl.isNullOrBlank() }
                HistoryFilter.ERRORS -> list.filter { !it.errorMessage.isNullOrBlank() }
            }

            when (selectedSort) {
                HistorySort.NEWEST -> list.sortedByDescending { it.timestampMs }
                HistorySort.OLDEST -> list.sortedBy { it.timestampMs }
                HistorySort.MOST_PRODUCTS -> list.sortedByDescending { it.productCount }
                HistorySort.LARGEST_SIZE -> list.sortedByDescending { it.fileSizeByte }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Dashboard Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Status & History",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Room-persisted database of all catalog extraction runs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (savedFiles.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showClearConfirmDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("btn_clear_history")
                    ) {
                        Icon(Icons.Default.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
        }

        // Active Extraction Live Status Banner (if currently extracting)
        if (isProcessingBatch || activeTasks.any { it.stage !is ProcessingStage.Queued && it.stage !is ProcessingStage.Completed && it.stage !is ProcessingStage.Error }) {
            item {
                ActiveLiveProgressBanner(activeTasks = activeTasks)
            }
        }

        // Metric Cards Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardMetricCard(
                        title = "Processed Catalogs",
                        value = "$totalFiles",
                        subtitle = "${formatBytes(totalBytesProcessed)} data",
                        icon = Icons.Default.PictureAsPdf,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )

                    DashboardMetricCard(
                        title = "Extracted Products",
                        value = "$totalProducts",
                        subtitle = if (totalFiles > 0) "~${totalProducts / totalFiles} per file" else "0 items",
                        icon = Icons.Default.TableChart,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardMetricCard(
                        title = "Success Rate",
                        value = "$successRate%",
                        subtitle = "$completedCount successful • $errorCount failed",
                        icon = Icons.Default.CheckCircle,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )

                    DashboardMetricCard(
                        title = "Drive Synced",
                        value = "$driveSyncedCount",
                        subtitle = "Cloud backed up",
                        icon = Icons.Default.CloudDone,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Search & Filter Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search catalog history...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_dashboard_search")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box {
                        OutlinedButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.testTag("btn_sort_history")
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                when (selectedSort) {
                                    HistorySort.NEWEST -> "Newest"
                                    HistorySort.OLDEST -> "Oldest"
                                    HistorySort.MOST_PRODUCTS -> "Items"
                                    HistorySort.LARGEST_SIZE -> "Size"
                                }
                            )
                        }

                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Newest First") },
                                onClick = { selectedSort = HistorySort.NEWEST; sortMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Oldest First") },
                                onClick = { selectedSort = HistorySort.OLDEST; sortMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Most Extracted Products") },
                                onClick = { selectedSort = HistorySort.MOST_PRODUCTS; sortMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Largest File Size") },
                                onClick = { selectedSort = HistorySort.LARGEST_SIZE; sortMenuExpanded = false }
                            )
                        }
                    }
                }

                // Filter Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == HistoryFilter.ALL,
                        onClick = { selectedFilter = HistoryFilter.ALL },
                        label = { Text("All ($totalFiles)") }
                    )
                    FilterChip(
                        selected = selectedFilter == HistoryFilter.COMPLETED,
                        onClick = { selectedFilter = HistoryFilter.COMPLETED },
                        label = { Text("Completed ($completedCount)") }
                    )
                    FilterChip(
                        selected = selectedFilter == HistoryFilter.DRIVE_SYNCED,
                        onClick = { selectedFilter = HistoryFilter.DRIVE_SYNCED },
                        label = { Text("Drive Uploaded ($driveSyncedCount)") }
                    )
                    FilterChip(
                        selected = selectedFilter == HistoryFilter.ERRORS,
                        onClick = { selectedFilter = HistoryFilter.ERRORS },
                        label = { Text("Errors ($errorCount)") }
                    )
                }
            }
        }

        // Empty state or Historical File Cards List
        if (filteredFiles.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (savedFiles.isEmpty()) "No processed catalogs yet" else "No matching historical records",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (savedFiles.isEmpty()) "Uploaded and extracted PDFs will be stored here in your local Room database." else "Try clearing your search query or changing active filters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(filteredFiles, key = { it.id }) { fileEntity ->
                HistoricalTaskCard(
                    file = fileEntity,
                    onInspectProducts = {
                        inspectingFile = fileEntity
                        coroutineScope.launch {
                            isLoadingProducts = true
                            fileProducts = onLoadProductsForFile(fileEntity.id)
                            isLoadingProducts = false
                        }
                    },
                    onOpenXlsx = { path ->
                        openOrShareFile(context, path, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    },
                    onOpenCsv = { path ->
                        openOrShareFile(context, path, "text/csv")
                    },
                    onOpenDriveUrl = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open URL: $url", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = {
                        onDeleteFile(fileEntity.id)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Clear History Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear All Extraction History?") },
            text = { Text("This will permanently remove all $totalFiles saved records and their extracted products from your local Room database. (Locally saved .xlsx & .csv files remain in storage).") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllHistory()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_clear_history")
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Extracted Products Inspection Dialog
    inspectingFile?.let { file ->
        InspectExtractedProductsDialog(
            file = file,
            products = fileProducts,
            isLoading = isLoadingProducts,
            onDismiss = {
                inspectingFile = null
                fileProducts = emptyList()
            }
        )
    }
}

@Composable
fun DashboardMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = contentColor.copy(alpha = 0.85f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = contentColor
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ActiveLiveProgressBanner(
    activeTasks: List<PdfProcessTask>,
    modifier: Modifier = Modifier
) {
    val inProgressTasks = activeTasks.filter {
        it.stage !is ProcessingStage.Queued && it.stage !is ProcessingStage.Completed && it.stage !is ProcessingStage.Error
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Live Extraction Pipeline (${inProgressTasks.size} task(s) active)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            inProgressTasks.forEach { task ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.fileName,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val stepNum = task.stage.stepNumber()
                            Text(
                                text = "Step $stepNum/6 • ${task.stage.label()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(task.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoricalTaskCard(
    file: ProcessedFileEntity,
    onInspectProducts: () -> Unit,
    onOpenXlsx: (String) -> Unit,
    onOpenCsv: (String) -> Unit,
    onOpenDriveUrl: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateString = remember(file.timestampMs) {
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        sdf.format(Date(file.timestampMs))
    }

    val isError = !file.errorMessage.isNullOrBlank()
    val isDriveUploaded = !file.driveXlsxUrl.isNullOrBlank()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_history_${file.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Status Badge + Date + Delete
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Status Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        isError -> MaterialTheme.colorScheme.errorContainer
                        isDriveUploaded -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isError -> Icons.Default.ErrorOutline
                                isDriveUploaded -> Icons.Default.CloudDone
                                else -> Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = when {
                                isError -> MaterialTheme.colorScheme.onErrorContainer
                                isDriveUploaded -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isError) "Failed" else if (isDriveUploaded) "Uploaded to Drive" else "Completed",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = when {
                                isError -> MaterialTheme.colorScheme.onErrorContainer
                                isDriveUploaded -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = dateString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("btn_delete_history_${file.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete from history",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // File Name
            Text(
                text = file.fileName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Stats Pills Row
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatsPill(label = "${file.productCount} Products", icon = Icons.Default.Inventory2)
                StatsPill(label = "${file.totalPages} Pages (${file.totalChunks} Chunks)", icon = Icons.Default.Description)
                StatsPill(label = formatBytes(file.fileSizeByte), icon = Icons.Default.Assessment)
            }

            // Error Message Banner if error occurred
            if (isError) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = file.errorMessage ?: "Unknown error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (file.productCount > 0) {
                    OutlinedButton(
                        onClick = onInspectProducts,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_inspect_${file.id}")
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Products", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (!file.xlsxFilePath.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onOpenXlsx(file.xlsxFilePath) },
                        modifier = Modifier.testTag("btn_open_xlsx_${file.id}")
                    ) {
                        Text("XLSX", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }

                if (!file.csvFilePath.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onOpenCsv(file.csvFilePath) },
                        modifier = Modifier.testTag("btn_open_csv_${file.id}")
                    ) {
                        Text("CSV", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (!file.driveXlsxUrl.isNullOrBlank()) {
                    IconButton(
                        onClick = { onOpenDriveUrl(file.driveXlsxUrl) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_drive_link_${file.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Open in Drive",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatsPill(label: String, icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun InspectExtractedProductsDialog(
    file: ProcessedFileEntity,
    products: List<ExtractedProductEntity>,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.fileName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${products.size} products extracted from this catalog",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading products from database...")
                    }
                } else if (products.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No products recorded for this file.", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(products) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.produit,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (item.brand.isNotBlank()) {
                                                Text(
                                                    text = item.brand,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (item.category.isNotBlank()) {
                                                Text(
                                                    text = "• ${item.category}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (item.unite.isNotBlank()) {
                                                Text(
                                                    text = "• ${item.unite}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${item.prix} €",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                        if (item.prixNormal.isNotBlank()) {
                                            Text(
                                                text = "${item.prixNormal} €",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

private fun openOrShareFile(context: Context, filePath: String, mimeType: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist on disk", Toast.LENGTH_SHORT).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share/Export File"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
