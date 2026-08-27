package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.DefaultSchemas
import com.example.model.ExtractionSchema
import com.example.model.SchemaField
import com.example.model.SchemaFieldType
import com.example.service.GeminiExtractor
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchemaManagerDialog(
    schemas: List<ExtractionSchema>,
    activeSchemaId: String,
    onSetActiveSchema: (String) -> Unit,
    onSaveSchema: (ExtractionSchema) -> Unit,
    onDeleteSchema: (String) -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Presets, 1 = Custom Schemas
    var editingSchema by remember { mutableStateOf<ExtractionSchema?>(null) }
    var previewingSchema by remember { mutableStateOf<ExtractionSchema?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val presets = schemas.filter { it.isPreset }
    val customSchemas = schemas.filter { !it.isPreset }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(20.dp))
                .testTag("dialog_schema_manager"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schema,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Data Extraction Schemas",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Save and reuse specific field schemas for consistent PDF extraction",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_schema_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tabs: Presets vs Custom
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Standard Presets (${presets.size})", fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("tab_preset_schemas")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Custom Schemas (${customSchemas.size})", fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("tab_custom_schemas")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Bar for Custom Schemas
                if (selectedTab == 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Saved Extraction Schemas",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = {
                                editingSchema = ExtractionSchema(
                                    id = UUID.randomUUID().toString(),
                                    name = "New Extraction Schema",
                                    description = "Custom extraction schema",
                                    isDefault = false,
                                    isPreset = false,
                                    fields = listOf(
                                        SchemaField(name = "Produit", label = "Product Name", type = SchemaFieldType.STRING, required = true, description = "Product commercial name"),
                                        SchemaField(name = "SKU", label = "SKU / Code", type = SchemaFieldType.STRING, description = "Item SKU or reference number"),
                                        SchemaField(name = "Prix", label = "Price", type = SchemaFieldType.PRICE, required = true, description = "Selling price numeric value")
                                    )
                                )
                            },
                            modifier = Modifier.testTag("btn_create_custom_schema")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Schema")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Schemas List
                val displayedSchemas = if (selectedTab == 0) presets else customSchemas

                if (displayedSchemas.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Icon(
                                Icons.Default.ListAlt,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Custom Schemas Yet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create a custom schema or duplicate a standard preset to customize extraction fields.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(0.8f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    editingSchema = ExtractionSchema(
                                        id = UUID.randomUUID().toString(),
                                        name = "Retail & SKU Catalog",
                                        description = "Custom product and SKU schema",
                                        fields = DefaultSchemas.RETAIL_SKU.fields
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create from Retail Preset")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayedSchemas, key = { it.id }) { schema ->
                            val isActive = schema.id == activeSchemaId
                            SchemaItemCard(
                                schema = schema,
                                isActive = isActive,
                                onSetActive = { onSetActiveSchema(schema.id) },
                                onEdit = { editingSchema = schema },
                                onDuplicate = {
                                    editingSchema = schema.copy(
                                        id = UUID.randomUUID().toString(),
                                        name = "${schema.name} (Copy)",
                                        isPreset = false,
                                        isDefault = false
                                    )
                                },
                                onDelete = { onDeleteSchema(schema.id) },
                                onPreviewPrompt = { previewingSchema = schema }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Presets")
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_done_schema_manager")
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }

    // Schema Editor Dialog
    if (editingSchema != null) {
        SchemaEditorDialog(
            initialSchema = editingSchema!!,
            onSave = { saved ->
                onSaveSchema(saved)
                editingSchema = null
            },
            onDismiss = { editingSchema = null }
        )
    }

    // Prompt & JSON Preview Dialog
    if (previewingSchema != null) {
        SchemaPreviewDialog(
            schema = previewingSchema!!,
            onDismiss = { previewingSchema = null }
        )
    }

    // Reset Confirm Dialog
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset to Default Presets?") },
            text = { Text("This will restore the standard extraction presets (Supermarket, Retail SKU, Electronics, B2B, Pharmacy) and delete all custom schemas.") },
            confirmButton = {
                Button(
                    onClick = {
                        onResetDefaults()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchemaItemCard(
    schema: ExtractionSchema,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onPreviewPrompt: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("schema_card_${schema.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = if (isActive)
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else
            CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = schema.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Active Schema", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        } else if (schema.isPreset) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Preset",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "Custom",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (schema.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = schema.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Field tags
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                schema.fields.forEach { field ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (field.type) {
                            SchemaFieldType.PRICE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            SchemaFieldType.DATE -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            SchemaFieldType.CATEGORY -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = "${field.name}${if (field.required) "*" else ""}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Set active
                if (!isActive) {
                    OutlinedButton(
                        onClick = onSetActive,
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Set as Active", fontSize = 12.sp)
                    }
                } else {
                    Text(
                        text = "✓ Used for new PDF uploads",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Right: Edit, Preview, Duplicate, Delete
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onPreviewPrompt,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "Preview AI Prompt", modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(18.dp))
                    }

                    if (!schema.isPreset) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SchemaEditorDialog(
    initialSchema: ExtractionSchema,
    onSave: (ExtractionSchema) -> Unit,
    onDismiss: () -> Unit
) {
    var schemaName by remember { mutableStateOf(initialSchema.name) }
    var schemaDescription by remember { mutableStateOf(initialSchema.description) }
    var fields by remember { mutableStateOf(initialSchema.fields.toMutableList()) }

    var editingField by remember { mutableStateOf<SchemaField?>(null) }
    var editingFieldIndex by remember { mutableIntStateOf(-1) }

    val commonSuggestions = listOf(
        SchemaField(name = "SKU", label = "SKU / Code", type = SchemaFieldType.STRING, description = "SKU, item code or reference"),
        SchemaField(name = "Barcode", label = "Barcode", type = SchemaFieldType.STRING, description = "Barcode digits, EAN or UPC"),
        SchemaField(name = "Prix_Normal", label = "Regular Price", type = SchemaFieldType.PRICE, description = "Original non-promotional price"),
        SchemaField(name = "Discount", label = "Discount", type = SchemaFieldType.STRING, description = "Discount percentage or savings amount"),
        SchemaField(name = "Date_Promo", label = "Validity Date", type = SchemaFieldType.DATE, description = "Validity date range of promo"),
        SchemaField(name = "Stock", label = "Stock / Qty", type = SchemaFieldType.STRING, description = "Stock availability or pack count"),
        SchemaField(name = "Warranty", label = "Warranty", type = SchemaFieldType.STRING, description = "Warranty period"),
        SchemaField(name = "Specs", label = "Tech Specs", type = SchemaFieldType.STRING, description = "Technical specifications")
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Edit Extraction Schema",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Schema Name & Description
                OutlinedTextField(
                    value = schemaName,
                    onValueChange = { schemaName = it },
                    label = { Text("Schema Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = schemaDescription,
                    onValueChange = { schemaDescription = it },
                    label = { Text("Description (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Suggestions Bar
                Text(
                    text = "Quick Add Popular Fields:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    commonSuggestions.forEach { suggestion ->
                        val alreadyAdded = fields.any { it.name.equals(suggestion.name, ignoreCase = true) }
                        FilterChip(
                            selected = alreadyAdded,
                            onClick = {
                                if (!alreadyAdded) {
                                    fields = (fields + suggestion.copy(id = UUID.randomUUID().toString())).toMutableList()
                                }
                            },
                            label = { Text("+ ${suggestion.label}", fontSize = 11.sp) },
                            leadingIcon = if (alreadyAdded) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Fields List Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Schema Fields (${fields.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    OutlinedButton(
                        onClick = {
                            editingField = SchemaField(
                                id = UUID.randomUUID().toString(),
                                name = "",
                                label = "",
                                type = SchemaFieldType.STRING,
                                description = ""
                            )
                            editingFieldIndex = -1
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Field")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Fields List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(fields) { index, field ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = field.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                text = field.type.displayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        if (field.required) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "(Required)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    if (field.label.isNotBlank() && field.label != field.name) {
                                        Text(
                                            text = "Label: ${field.label}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (field.description.isNotBlank()) {
                                        Text(
                                            text = field.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Reorder actions
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val list = fields.toMutableList()
                                            val item = list.removeAt(index)
                                            list.add(index - 1, item)
                                            fields = list
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                }

                                IconButton(
                                    onClick = {
                                        if (index < fields.size - 1) {
                                            val list = fields.toMutableList()
                                            val item = list.removeAt(index)
                                            list.add(index + 1, item)
                                            fields = list
                                        }
                                    },
                                    enabled = index < fields.size - 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                }

                                // Edit field
                                IconButton(
                                    onClick = {
                                        editingField = field
                                        editingFieldIndex = index
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Field", modifier = Modifier.size(16.dp))
                                }

                                // Delete field
                                IconButton(
                                    onClick = {
                                        val list = fields.toMutableList()
                                        list.removeAt(index)
                                        fields = list
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Field", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val sanitized = initialSchema.copy(
                                name = schemaName.trim().ifBlank { "Custom Schema" },
                                description = schemaDescription.trim(),
                                fields = fields
                            )
                            onSave(sanitized)
                        },
                        enabled = schemaName.isNotBlank() && fields.isNotEmpty()
                    ) {
                        Text("Save Schema")
                    }
                }
            }
        }
    }

    // Individual Field Editor Dialog
    if (editingField != null) {
        FieldEditorSubDialog(
            initialField = editingField!!,
            onSave = { savedField ->
                val list = fields.toMutableList()
                if (editingFieldIndex >= 0 && editingFieldIndex < list.size) {
                    list[editingFieldIndex] = savedField
                } else {
                    list.add(savedField)
                }
                fields = list
                editingField = null
                editingFieldIndex = -1
            },
            onDismiss = {
                editingField = null
                editingFieldIndex = -1
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldEditorSubDialog(
    initialField: SchemaField,
    onSave: (SchemaField) -> Unit,
    onDismiss: () -> Unit
) {
    var fieldName by remember { mutableStateOf(initialField.name) }
    var fieldLabel by remember { mutableStateOf(initialField.label) }
    var fieldType by remember { mutableStateOf(initialField.type) }
    var fieldDescription by remember { mutableStateOf(initialField.description) }
    var isRequired by remember { mutableStateOf(initialField.required) }

    var typeDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialField.name.isBlank()) "Add Schema Field" else "Edit Field") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = fieldName,
                    onValueChange = {
                        fieldName = it.replace(" ", "_")
                        if (fieldLabel.isBlank()) {
                            fieldLabel = it
                        }
                    },
                    label = { Text("Field Key (JSON Key) *") },
                    placeholder = { Text("e.g. SKU, Prix, Barcode") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = fieldLabel,
                    onValueChange = { fieldLabel = it },
                    label = { Text("Display Label (Table & Excel)") },
                    placeholder = { Text("e.g. SKU / Item Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = typeDropdownExpanded,
                    onExpandedChange = { typeDropdownExpanded = !typeDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = fieldType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Data Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false }
                    ) {
                        SchemaFieldType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    fieldType = type
                                    typeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = fieldDescription,
                    onValueChange = { fieldDescription = it },
                    label = { Text("AI Extraction Instruction") },
                    placeholder = { Text("e.g. Extract the SKU or reference code printed next to title") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isRequired = !isRequired }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(checked = isRequired, onCheckedChange = { isRequired = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Required field (Gemini must prioritize extracting this)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialField.copy(
                            name = fieldName.trim(),
                            label = fieldLabel.trim().ifBlank { fieldName.trim() },
                            type = fieldType,
                            description = fieldDescription.trim(),
                            required = isRequired
                        )
                    )
                },
                enabled = fieldName.isNotBlank()
            ) {
                Text("Save Field")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SchemaPreviewDialog(
    schema: ExtractionSchema,
    onDismiss: () -> Unit
) {
    val prompt = remember(schema) { GeminiExtractor.buildPromptForSchema(schema) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(18.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Prompt & Schema Preview",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Schema: ${schema.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    ) {
                        Text(
                            text = prompt,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
