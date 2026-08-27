package com.example.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pattern
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.FieldValidationRule
import com.example.model.ValidationAction
import java.util.UUID

private val AVAILABLE_FIELDS = listOf(
    "Produit",
    "Prix",
    "Prix_Normal",
    "Brand",
    "Category",
    "Date_Promo",
    "unité"
)

private data class PresetPattern(
    val label: String,
    val field: String,
    val pattern: String,
    val description: String
)

private val PRESET_PATTERNS = listOf(
    PresetPattern("Decimal Price (e.g. 19.99)", "Prix", "^\\d+(\\.\\d{1,2})?$", "Valid positive numeric price"),
    PresetPattern("Min 3 Characters", "Produit", "^.{3,}$", "Minimum 3 characters long"),
    PresetPattern("Alphanumeric Brand", "Brand", "^[a-zA-Z0-9\\s&'./-]*$", "Standard brand characters"),
    PresetPattern("Standard Unit (kg, L, etc.)", "unité", "^[a-zA-Z0-9\\s./%xX-]*$", "Valid measurement unit"),
    PresetPattern("Percentage (e.g. -20% OFF)", "Date_Promo", ".*(\\d{1,2}%|-?\\d+%).*", "Contains promotional percentage"),
    PresetPattern("Barcode/EAN (8-14 digits)", "Produit", ".*\\b\\d{8,14}\\b.*", "Contains barcode/EAN number")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegexValidationConfigDialog(
    rules: List<FieldValidationRule>,
    onSaveRules: (List<FieldValidationRule>) -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    var ruleList by remember(rules) { mutableStateOf(rules) }
    var editingRule by remember { mutableStateOf<FieldValidationRule?>(null) }
    var isAddingRule by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(20.dp)),
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
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pattern,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Regex Field Validation",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${ruleList.count { it.isEnabled }} active validation rule(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            onResetDefaults()
                        },
                        modifier = Modifier.testTag("btn_reset_validation_defaults")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Defaults")
                    }

                    Button(
                        onClick = {
                            editingRule = null
                            isAddingRule = true
                        },
                        modifier = Modifier.testTag("btn_add_regex_rule")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Custom Rule")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Rules List
                if (ruleList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Rule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No validation rules configured",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add rules or reset to defaults to validate extracted catalog fields.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ruleList, key = { it.id }) { rule ->
                            ValidationRuleCard(
                                rule = rule,
                                onToggle = { enabled ->
                                    val updated = ruleList.map {
                                        if (it.id == rule.id) it.copy(isEnabled = enabled) else it
                                    }
                                    ruleList = updated
                                    onSaveRules(updated)
                                },
                                onEdit = {
                                    editingRule = rule
                                    isAddingRule = true
                                },
                                onDelete = {
                                    val updated = ruleList.filter { it.id != rule.id }
                                    ruleList = updated
                                    onSaveRules(updated)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Done Button
                Button(
                    onClick = {
                        onSaveRules(ruleList)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_close_regex_config")
                ) {
                    Text("Apply & Done")
                }
            }
        }
    }

    // Add / Edit Rule Modal Dialog
    if (isAddingRule) {
        RuleEditorDialog(
            existingRule = editingRule,
            onSave = { savedRule ->
                val updated = if (editingRule != null) {
                    ruleList.map { if (it.id == savedRule.id) savedRule else it }
                } else {
                    ruleList + savedRule
                }
                ruleList = updated
                onSaveRules(updated)
                isAddingRule = false
                editingRule = null
            },
            onDismiss = {
                isAddingRule = false
                editingRule = null
            }
        )
    }
}

@Composable
fun ValidationRuleCard(
    rule: FieldValidationRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isEnabled)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Field Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = rule.fieldName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Action on Mismatch Badge
                val actionColor = when (rule.actionOnMismatch) {
                    ValidationAction.FILTER_EXCLUDE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                    ValidationAction.FLAG_WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                    ValidationAction.SET_EMPTY -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = actionColor.first
                ) {
                    Text(
                        text = when (rule.actionOnMismatch) {
                            ValidationAction.FILTER_EXCLUDE -> "Exclude Item"
                            ValidationAction.FLAG_WARNING -> "Flag Warning"
                            ValidationAction.SET_EMPTY -> "Clear Field"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = actionColor.second,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Toggle switch
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("switch_rule_${rule.id}")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Description
            Text(
                text = rule.description,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Pattern Box
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = rule.pattern,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Edit & Delete actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Rule",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Rule",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorDialog(
    existingRule: FieldValidationRule?,
    onSave: (FieldValidationRule) -> Unit,
    onDismiss: () -> Unit
) {
    var fieldName by remember { mutableStateOf(existingRule?.fieldName ?: AVAILABLE_FIELDS[0]) }
    var pattern by remember { mutableStateOf(existingRule?.pattern ?: "") }
    var description by remember { mutableStateOf(existingRule?.description ?: "") }
    var actionOnMismatch by remember { mutableStateOf(existingRule?.actionOnMismatch ?: ValidationAction.FLAG_WARNING) }

    // Live Regex Playground test state
    var testInput by remember { mutableStateOf("") }
    var fieldDropdownExpanded by remember { mutableStateOf(false) }

    val isRegexValid by remember(pattern) {
        derivedStateOf {
            if (pattern.isBlank()) false
            else {
                try {
                    Regex(pattern)
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    val testMatchResult by remember(pattern, testInput) {
        derivedStateOf {
            if (pattern.isBlank() || testInput.isBlank()) null
            else {
                try {
                    val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                    regex.containsMatchIn(testInput) || regex.matches(testInput)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingRule != null) "Edit Validation Rule" else "Add Validation Rule",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Preset chips
                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PRESET_PATTERNS.forEach { preset ->
                        AssistChip(
                            onClick = {
                                fieldName = preset.field
                                pattern = preset.pattern
                                description = preset.description
                            },
                            label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                // Field Selector
                ExposedDropdownMenuBox(
                    expanded = fieldDropdownExpanded,
                    onExpandedChange = { fieldDropdownExpanded = !fieldDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = fieldName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Field") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fieldDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = fieldDropdownExpanded,
                        onDismissRequest = { fieldDropdownExpanded = false }
                    ) {
                        AVAILABLE_FIELDS.forEach { field ->
                            DropdownMenuItem(
                                text = { Text(field) },
                                onClick = {
                                    fieldName = field
                                    fieldDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Regex Pattern Input
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Regex Pattern") },
                    placeholder = { Text("^\\d+(\\.\\d{1,2})?$") },
                    isError = pattern.isNotBlank() && !isRegexValid,
                    supportingText = {
                        if (pattern.isNotBlank() && !isRegexValid) {
                            Text("Invalid regular expression syntax", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Supports standard Java/Kotlin regex patterns")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_regex_pattern")
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Rule Description") },
                    placeholder = { Text("e.g. Must be a valid positive price") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Action on Mismatch
                Text(
                    text = "Action when validation fails:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = actionOnMismatch == ValidationAction.FLAG_WARNING,
                        onClick = { actionOnMismatch = ValidationAction.FLAG_WARNING },
                        label = { Text("Flag Warning", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (actionOnMismatch == ValidationAction.FLAG_WARNING) {
                            { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )

                    FilterChip(
                        selected = actionOnMismatch == ValidationAction.FILTER_EXCLUDE,
                        onClick = { actionOnMismatch = ValidationAction.FILTER_EXCLUDE },
                        label = { Text("Exclude Item", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (actionOnMismatch == ValidationAction.FILTER_EXCLUDE) {
                            { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )

                    FilterChip(
                        selected = actionOnMismatch == ValidationAction.SET_EMPTY,
                        onClick = { actionOnMismatch = ValidationAction.SET_EMPTY },
                        label = { Text("Clear Field", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                // Interactive Regex Tester / Playground
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Live Regex Tester Playground",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = testInput,
                            onValueChange = { testInput = it },
                            label = { Text("Test Sample String") },
                            placeholder = { Text("e.g. 19.99 or Fresh Milk") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_regex_tester")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (testInput.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (testMatchResult == true) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Matches Pattern! (Validation passes)",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFF2E7D32)
                                    )
                                } else if (testMatchResult == false) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Does NOT match pattern (Validation fails)",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isRegexValid && pattern.isNotBlank()) {
                        val rule = FieldValidationRule(
                            id = existingRule?.id ?: "rule_${UUID.randomUUID().toString().take(8)}",
                            fieldName = fieldName,
                            pattern = pattern.trim(),
                            description = description.ifBlank { "Matches $pattern on $fieldName" },
                            isEnabled = true,
                            actionOnMismatch = actionOnMismatch,
                            isCustom = true
                        )
                        onSave(rule)
                    }
                },
                enabled = isRegexValid && pattern.isNotBlank(),
                modifier = Modifier.testTag("btn_save_regex_rule")
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
