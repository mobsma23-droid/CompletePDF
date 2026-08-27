package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.PdfProcessTask
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CaseTransformation(val label: String) {
    AS_IS("Original"),
    LOWERCASE("lowercase"),
    UPPERCASE("UPPERCASE"),
    TITLE_CASE("Title Case"),
    SNAKE_CASE("snake_case"),
    KEBAB_CASE("kebab-case")
}

data class BulkRenameConfig(
    val pattern: String = "{name}_{date}",
    val prefix: String = "",
    val suffix: String = "",
    val startIndex: Int = 1,
    val paddingDigits: Int = 2,
    val caseFormat: CaseTransformation = CaseTransformation.AS_IS
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BulkRenameDialog(
    tasks: List<PdfProcessTask>,
    onDismiss: () -> Unit,
    onApplyRename: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var pattern by remember { mutableStateOf("{name}_{date}_{index}") }
    var prefix by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var startIndex by remember { mutableIntStateOf(1) }
    var paddingDigits by remember { mutableIntStateOf(2) }
    var selectedCase by remember { mutableStateOf(CaseTransformation.AS_IS) }
    var showAdvanced by remember { mutableStateOf(false) }

    val currentDateStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    // Helper to compute new name for a task
    fun computeNewName(task: PdfProcessTask, indexOffset: Int): String {
        val originalBase = task.fileName.removeSuffix(".pdf").removeSuffix(".PDF")
        val taskIndex = startIndex + indexOffset
        val formattedIndex = String.format(Locale.US, "%0${paddingDigits}d", taskIndex)
        val brand = task.products.firstOrNull { it.Brand.isNotBlank() }?.Brand
            ?: task.products.firstOrNull { it.Produit.isNotBlank() }?.Produit?.split(" ")?.firstOrNull()
            ?: "Catalog"
        val count = task.products.size.toString()

        var result = pattern
            .replace("{name}", originalBase, ignoreCase = true)
            .replace("{original}", originalBase, ignoreCase = true)
            .replace("{date}", currentDateStr, ignoreCase = true)
            .replace("{index}", formattedIndex, ignoreCase = true)
            .replace("{num}", formattedIndex, ignoreCase = true)
            .replace("{brand}", brand, ignoreCase = true)
            .replace("{supermarket}", brand, ignoreCase = true)
            .replace("{count}", count, ignoreCase = true)

        if (prefix.isNotBlank()) {
            result = "${prefix.trim()}$result"
        }
        if (suffix.isNotBlank()) {
            result = "$result${suffix.trim()}"
        }

        // Apply case transformation
        result = when (selectedCase) {
            CaseTransformation.AS_IS -> result
            CaseTransformation.LOWERCASE -> result.lowercase(Locale.getDefault())
            CaseTransformation.UPPERCASE -> result.uppercase(Locale.getDefault())
            CaseTransformation.TITLE_CASE -> result.split(" ", "_", "-")
                .filter { it.isNotBlank() }
                .joinToString("_") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            CaseTransformation.SNAKE_CASE -> result
                .replace(Regex("[\\s\\-]+"), "_")
                .replace(Regex("[^a-zA-Z0-9_]"), "")
                .lowercase(Locale.getDefault())
            CaseTransformation.KEBAB_CASE -> result
                .replace(Regex("[\\s_]+"), "-")
                .replace(Regex("[^a-zA-Z0-9\\-]"), "")
                .lowercase(Locale.getDefault())
        }

        // Clean double separators and ensure valid file name
        result = result
            .replace(Regex("_+"), "_")
            .replace(Regex("-+"), "-")
            .trim('_', '-', ' ')

        if (result.isBlank()) {
            result = originalBase
        }

        return if (result.endsWith(".pdf", ignoreCase = true)) result else "$result.pdf"
    }

    // Map task id to preview new name
    val previewRenamedMap = remember(tasks, pattern, prefix, suffix, startIndex, paddingDigits, selectedCase) {
        tasks.mapIndexed { index, task ->
            task.id to computeNewName(task, index)
        }.toMap()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DriveFileRenameOutline,
                                    contentDescription = "Bulk Rename",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Bulk Rename Files",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Define naming pattern for ${tasks.size} processed file(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_bulk_rename_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Presets Horizontal Row
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                val presets = listOf(
                    "{name}_{date}_{index}",
                    "{brand}_{date}_{num}",
                    "Catalog_{date}_{num}",
                    "{name}_cleaned",
                    "Promo_{brand}_{count}items"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = pattern == preset,
                            onClick = { pattern = preset },
                            label = { Text(preset, fontSize = 12.sp) },
                            modifier = Modifier.testTag("preset_chip_$preset")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pattern Input Box
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Naming Pattern Template") },
                    placeholder = { Text("{name}_{date}_{index}") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("naming_pattern_input")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Variable insertion chips
                Text(
                    text = "Insert Placeholder Variables:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(4.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("{name}", "{date}", "{index}", "{brand}", "{count}").forEach { placeholder ->
                        AssistChip(
                            onClick = {
                                pattern = if (pattern.isBlank()) placeholder else "${pattern}_$placeholder"
                            },
                            label = { Text(placeholder, fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add $placeholder",
                                    modifier = Modifier.size(12.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.testTag("insert_placeholder_${placeholder.trim('{', '}')}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Advanced Options Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showAdvanced) "Hide Formatting Options" else "Show Formatting Options (Prefix, Case, Index)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (showAdvanced) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(10.dp)
                    ) {
                        // Prefix & Suffix
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = prefix,
                                onValueChange = { prefix = it },
                                label = { Text("Prefix", fontSize = 12.sp) },
                                placeholder = { Text("PROMO_") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("rename_prefix_input")
                            )

                            OutlinedTextField(
                                value = suffix,
                                onValueChange = { suffix = it },
                                label = { Text("Suffix", fontSize = 12.sp) },
                                placeholder = { Text("_FINAL") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("rename_suffix_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Case Formatting Chips
                        Text(
                            text = "Text Case:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CaseTransformation.values().forEach { caseFormat ->
                                FilterChip(
                                    selected = selectedCase == caseFormat,
                                    onClick = { selectedCase = caseFormat },
                                    label = { Text(caseFormat.label, fontSize = 11.sp) },
                                    modifier = Modifier.testTag("case_chip_${caseFormat.name}")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Preview Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Rename Preview (${tasks.size} files)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Preview List Container
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(tasks) { index, task ->
                            val newName = previewRenamedMap[task.id] ?: task.fileName

                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.width(24.dp)
                                    )

                                    // Original Name
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.fileName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "to",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp)
                                            .size(16.dp)
                                    )

                                    // New Name
                                    Column(modifier = Modifier.weight(1.3f)) {
                                        Text(
                                            text = newName,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_bulk_rename_button")
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            onApplyRename(previewRenamedMap)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("confirm_bulk_rename_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DriveFileRenameOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply Rename (${tasks.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
