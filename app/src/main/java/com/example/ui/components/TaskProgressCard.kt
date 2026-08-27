package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.model.ExtractionStepItem
import com.example.model.PdfProcessTask
import com.example.model.ProcessingStage
import com.example.model.StepStatus
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TaskProgressCard(
    task: PdfProcessTask,
    queueIndex: Int? = null,
    totalQueueCount: Int? = null,
    onRemove: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onPreview: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onChangeSchema: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isActivelyProcessing = task.stage !is ProcessingStage.Completed && 
                              task.stage !is ProcessingStage.Error && 
                              task.stage !is ProcessingStage.Queued

    var showStepPipeline by remember(isActivelyProcessing) { 
        mutableStateOf(isActivelyProcessing) 
    }
    var showLogs by remember { mutableStateOf(false) }

    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        task.stage is ProcessingStage.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        task.stage is ProcessingStage.Completed -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
        task.stage !is ProcessingStage.Queued -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    val cardBorder = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else null

    val animatedProgress by animateFloatAsState(
        targetValue = task.progress,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "task_progress_anim"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode && onToggleSelect != null) {
                        onToggleSelect()
                    }
                },
                onLongClick = onLongClick
            )
            .testTag("task_card_${task.id}"),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = cardBorder,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header Row: Queue Number Badge, Checkbox, File Name, Schema Badge & Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect?.invoke() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .testTag("select_checkbox_${task.id}")
                                .padding(end = 6.dp)
                        )
                    } else if (queueIndex != null) {
                        // Queue position badge
                        Surface(
                            shape = CircleShape,
                            color = when (task.stage) {
                                is ProcessingStage.Completed -> MaterialTheme.colorScheme.primary
                                is ProcessingStage.Error -> MaterialTheme.colorScheme.error
                                is ProcessingStage.Queued -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "#${queueIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = when (task.stage) {
                                        is ProcessingStage.Completed -> MaterialTheme.colorScheme.onPrimary
                                        is ProcessingStage.Error -> MaterialTheme.colorScheme.onError
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "PDF File",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.fileName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = task.formattedSize,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            val isSynced = !task.driveXlsxUrl.isNullOrBlank() || !task.driveCsvUrl.isNullOrBlank()
                            val isUploading = task.stage is ProcessingStage.UploadingDrive

                            StorageSyncBadge(
                                isSyncedToDrive = isSynced,
                                isUploading = isUploading
                            )

                            if (task.isLargeFile) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = ">50MB Auto-Split",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Top right actions: Move Up, Move Down, Delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.stage is ProcessingStage.Queued && !isSelectionMode) {
                        if (onMoveUp != null) {
                            IconButton(
                                onClick = onMoveUp,
                                modifier = Modifier.size(28.dp).testTag("move_up_${task.id}")
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up in Queue", modifier = Modifier.size(16.dp))
                            }
                        }
                        if (onMoveDown != null) {
                            IconButton(
                                onClick = onMoveDown,
                                modifier = Modifier.size(28.dp).testTag("move_down_${task.id}")
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down in Queue", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if (!isSelectionMode) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(28.dp).testTag("remove_task_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove Task",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Schema Badge & Stage Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Schema pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = if (task.stage is ProcessingStage.Queued && onChangeSchema != null) {
                        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onChangeSchema)
                    } else Modifier
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schema,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = task.schemaName.ifBlank { "Supermarket Flyer" },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 11.sp
                        )
                    }
                }

                StatusBadge(stage = task.stage)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Real-Time Progress Indicator & Stats Bar
            if (task.stage !is ProcessingStage.Queued) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val stepNum = task.stage.stepNumber()
                            Text(
                                text = if (task.stage is ProcessingStage.Completed) "All 6 Steps Complete" else "Step $stepNum of 6",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (task.stage is ProcessingStage.Completed) Color(0xFF1B5E20) else MaterialTheme.colorScheme.primary
                            )
                            if (task.products.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${task.products.size} items extracted",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (task.stage is ProcessingStage.Completed) Color(0xFF1B5E20) else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (task.stage is ProcessingStage.Completed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                    )

                    // Current step real-time sub-detail
                    if (task.currentStepDetail != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = task.currentStepDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Step Pipeline Accordion Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { showStepPipeline = !showStepPipeline }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Extraction Step Pipeline (6 Steps)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (showStepPipeline) "Hide" else "Show Details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.sp
                    )
                    Icon(
                        imageVector = if (showStepPipeline) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Collapsible Step Pipeline
            AnimatedVisibility(
                visible = showStepPipeline,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    val steps = remember(task.stage, task.progress, task.totalPages, task.totalChunks, task.products.size) {
                        task.getExtractionSteps()
                    }

                    steps.forEachIndexed { index, step ->
                        ExtractionStepRow(
                            step = step,
                            isLast = index == steps.size - 1
                        )
                    }

                    // Optional Step Logs Expander
                    if (task.stepLogs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showLogs = !showLogs }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ListAlt, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Live Activity Logs (${task.stepLogs.size})", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), fontSize = 11.sp)
                                    }
                                    Icon(
                                        imageVector = if (showLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                if (showLogs) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        task.stepLogs.takeLast(6).forEach { log ->
                                            Text(
                                                text = log,
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Error Message with Retry Action
            if (task.stage is ProcessingStage.Error) {
                val msg = (task.stage as ProcessingStage.Error).message
                Spacer(modifier = Modifier.height(6.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    if (onRetry != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .testTag("retry_task_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry Extraction",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry Extraction", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Drive Upload Notice if any
            if (task.driveError != null && task.stage is ProcessingStage.Completed) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Drive: ${task.driveError}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp
                )
            }

            // Output Actions Row (Preview Table, Excel, CSV, Drive Link)
            AnimatedVisibility(visible = task.stage is ProcessingStage.Completed || task.products.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    // Preview Button
                    if (onPreview != null) {
                        FilledTonalButton(
                            onClick = onPreview,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .testTag("preview_task_${task.id}"),
                            contentPadding = ButtonDefaults.ContentPadding
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Preview Table",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview Extracted Table (${task.products.size} Items)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Open Excel .xlsx
                        if (task.xlsxFilePath != null) {
                            Button(
                                onClick = { shareOrOpenFile(context, File(task.xlsxFilePath), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag("open_xlsx_${task.id}"),
                                contentPadding = ButtonDefaults.ContentPadding
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TableChart,
                                    contentDescription = "Open Excel",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Excel (.xlsx)", fontSize = 12.sp)
                            }
                        }

                        // Open CSV
                        if (task.csvFilePath != null) {
                            OutlinedButton(
                                onClick = { shareOrOpenFile(context, File(task.csvFilePath), "text/csv") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag("open_csv_${task.id}"),
                                contentPadding = ButtonDefaults.ContentPadding
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Open CSV",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CSV", fontSize = 12.sp)
                            }
                        }
                    }

                    // Google Drive Link
                    if (task.driveXlsxUrl != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(task.driveXlsxUrl))
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .testTag("open_drive_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "View on Drive",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View in Google Drive Folder", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExtractionStepRow(
    step: ExtractionStepItem,
    isLast: Boolean = false,
    modifier: Modifier = Modifier
) {
    val stepIcon: ImageVector = when (step.stepKey) {
        "inspect" -> Icons.Default.PictureAsPdf
        "chunk" -> Icons.Default.Layers
        "ai_extract" -> Icons.Default.AutoAwesome
        "validate" -> Icons.Default.Rule
        "export" -> Icons.Default.TableChart
        "sync" -> Icons.Default.CloudUpload
        else -> Icons.Default.Check
    }

    val (badgeBgColor, badgeTextColor) = when (step.status) {
        StepStatus.COMPLETED -> Pair(Color(0xFFE8F5E9), Color(0xFF1B5E20))
        StepStatus.IN_PROGRESS -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
        StepStatus.FAILED -> Pair(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
        StepStatus.SKIPPED -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.outline)
        StepStatus.PENDING -> Pair(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Step Indicator Icon & Connecting Line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(26.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = badgeBgColor,
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when (step.status) {
                        StepStatus.COMPLETED -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = badgeTextColor,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        StepStatus.IN_PROGRESS -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = badgeTextColor
                            )
                        }
                        StepStatus.FAILED -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = badgeTextColor,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        else -> {
                            Text(
                                text = "${step.stepIndex}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = badgeTextColor,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(18.dp)
                        .background(
                            if (step.status == StepStatus.COMPLETED) Color(0xFF81C784) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Step Content (Title, Description, Detail)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = stepIcon,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (step.status == StepStatus.IN_PROGRESS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (step.status == StepStatus.IN_PROGRESS) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (step.status == StepStatus.IN_PROGRESS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeBgColor
                ) {
                    Text(
                        text = when (step.status) {
                            StepStatus.COMPLETED -> "Done"
                            StepStatus.IN_PROGRESS -> "In Progress"
                            StepStatus.FAILED -> "Failed"
                            StepStatus.SKIPPED -> "Skipped"
                            StepStatus.PENDING -> "Pending"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        fontSize = 9.sp
                    )
                }
            }

            if (step.detail != null && step.status != StepStatus.PENDING) {
                Text(
                    text = step.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (step.status == StepStatus.IN_PROGRESS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun StatusBadge(stage: ProcessingStage) {
    val (bgColor, textColor, label) = when (stage) {
        is ProcessingStage.Queued -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Queued")
        is ProcessingStage.Analyzing -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, "Step 1/6: Analyzing...")
        is ProcessingStage.Splitting -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, "Step 2/6: Chunks (${stage.chunkIndex}/${stage.totalChunks})")
        is ProcessingStage.Extracting -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            if (stage.itemsFoundSoFar > 0) "Step 3/6: AI (${stage.chunkIndex}/${stage.totalChunks} • ${stage.itemsFoundSoFar} items)" else "Step 3/6: AI (${stage.chunkIndex}/${stage.totalChunks})"
        )
        is ProcessingStage.Validating -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, "Step 4/6: Validating (${stage.itemsCount} items)")
        is ProcessingStage.GeneratingFiles -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, "Step 5/6: Building Files")
        is ProcessingStage.UploadingDrive -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "Step 6/6: Drive Sync")
        is ProcessingStage.Completed -> Triple(Color(0xFFE8F5E9), Color(0xFF1B5E20), "Completed")
        is ProcessingStage.Error -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "Failed")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            if (stage !is ProcessingStage.Completed && stage !is ProcessingStage.Error && stage !is ProcessingStage.Queued) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 2.dp,
                    color = textColor
                )
                Spacer(modifier = Modifier.width(4.dp))
            } else if (stage is ProcessingStage.Completed) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = textColor,
                fontSize = 11.sp
            )
        }
    }
}

private fun shareOrOpenFile(context: Context, file: File, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Open or Share ${file.name}")
        context.startActivity(chooser)
    } catch (e: Exception) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                setType(mimeType)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${file.name}"))
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }
}

@Composable
fun StorageSyncBadge(
    isSyncedToDrive: Boolean,
    isUploading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon, label) = when {
        isUploading -> Quadruple(
            Color(0xFFE3F2FD),
            Color(0xFF0D47A1),
            Icons.Default.CloudUpload,
            "Syncing..."
        )
        isSyncedToDrive -> Quadruple(
            Color(0xFFE8F5E9),
            Color(0xFF1B5E20),
            Icons.Default.CloudDone,
            "Google Drive"
        )
        else -> Quadruple(
            Color(0xFFFFF3E0),
            Color(0xFFE65100),
            Icons.Default.Storage,
            "Local"
        )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = textColor,
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = textColor,
                fontSize = 10.sp
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
