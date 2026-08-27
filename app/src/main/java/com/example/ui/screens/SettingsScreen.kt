package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pattern
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.AppThemeMode
import com.example.model.ExtractionSchema
import com.example.model.FieldValidationRule
import com.example.service.GoogleDriveUploader
import com.example.ui.components.RegexValidationConfigDialog
import com.example.ui.components.SchemaManagerDialog
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    driveToken: String,
    onDriveTokenSave: (String) -> Unit,
    driveFolderId: String,
    onDriveFolderIdSave: (String) -> Unit,
    driveAutoUpload: Boolean = true,
    onDriveAutoUploadChange: (Boolean) -> Unit = {},
    backgroundProcessingEnabled: Boolean = true,
    onBackgroundProcessingChange: (Boolean) -> Unit = {},
    validationRules: List<FieldValidationRule> = emptyList(),
    onSaveValidationRules: (List<FieldValidationRule>) -> Unit = {},
    onResetValidationRules: () -> Unit = {},
    allSchemas: List<ExtractionSchema> = emptyList(),
    activeSchemaId: String = "",
    onSetActiveSchema: (String) -> Unit = {},
    onSaveSchema: (ExtractionSchema) -> Unit = {},
    onDeleteSchema: (String) -> Unit = {},
    onResetSchemasDefaults: () -> Unit = {},
    userName: String? = null,
    userEmail: String? = null,
    isGuest: Boolean = false,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var tempToken by remember(driveToken) { mutableStateOf(driveToken) }
    var tempFolder by remember(driveFolderId) { mutableStateOf(driveFolderId) }
    var showRegexDialog by remember { mutableStateOf(false) }
    var showSchemaManagerDialog by remember { mutableStateOf(false) }

    val activeSchema = allSchemas.find { it.id == activeSchemaId } ?: allSchemas.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Heading
        Column {
            Text(
                text = "Preferences & Configuration",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Configure data extraction schemas, cloud sync, validation rules, and background engine",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Account / Profile Card
        if (!userName.isNullOrBlank() || !userEmail.isNullOrBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_settings_account"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "User Avatar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userName ?: "User",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isGuest) "Offline / Guest Session" else (userEmail ?: "Authenticated with Google"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("btn_sign_out")
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign Out")
                    }
                }
            }
        }

        // Data Extraction Schema Configuration Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_settings_schemas"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schema,
                            contentDescription = "Extraction Schemas",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Data Extraction Schemas",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Active: ${activeSchema?.name ?: "Supermarket & Grocery Flyer"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Supermarket & Grocery Flyer schema extracting product name, brand, category, promotional price, regular price, validity date, and packaging unit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showSchemaManagerDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_open_schema_manager_from_settings")
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View Schema Fields & Details")
                }
            }
        }

        // Google Drive Integration & Auto-Upload Card (Requires OAuth Bearer Token)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_settings_drive"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "Google Drive",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Drive Cloud Sync",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Requires OAuth Bearer Token for authorization",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Auto-Upload Switch Row
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (driveAutoUpload)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Upload Processed Files",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (driveAutoUpload)
                                    "Automatically upload .xlsx & .csv to Google Drive upon extraction success"
                                else
                                    "Save files locally only (manual upload on demand)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = driveAutoUpload,
                            onCheckedChange = onDriveAutoUploadChange,
                            modifier = Modifier.testTag("switch_drive_auto_upload")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = tempFolder,
                    onValueChange = { tempFolder = it },
                    label = { Text("Target Drive Folder ID") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drive_folder_id_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Mandatory OAuth Bearer Token input for Google Drive sync
                OutlinedTextField(
                    value = tempToken,
                    onValueChange = { tempToken = it },
                    label = { Text("OAuth Bearer Token (Required)") },
                    placeholder = { Text("ya29.a0A...") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = if (tempToken.isBlank())
                                "A valid Google Drive OAuth Bearer token is required to upload files."
                            else
                                "OAuth Bearer token configured for Drive upload API calls."
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drive_oauth_token_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        onDriveFolderIdSave(tempFolder)
                        onDriveTokenSave(tempToken)
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("save_drive_settings_button")
                ) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Drive Config")
                }
            }
        }

        // Custom Regex Field Validation Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_settings_regex_validation"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pattern,
                            contentDescription = "Regex Patterns",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Custom Regex Validation",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${validationRules.count { it.isEnabled }} active rule(s) configured",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Configure custom regular expression patterns to automatically validate and sanitize product names, prices, brands, dates, and units during AI catalog parsing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showRegexDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_open_regex_manager")
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Configure Field Patterns & Regex Tester")
                }
            }
        }

        // Background Execution & Processing Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_settings_background"),
            colors = CardDefaults.cardColors(
                containerColor = if (backgroundProcessingEnabled)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                else
                    MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Background Processing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Execution",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (backgroundProcessingEnabled) "Active • Runs when app is minimized" else "Disabled",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (backgroundProcessingEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = backgroundProcessingEnabled,
                        onCheckedChange = onBackgroundProcessingChange,
                        modifier = Modifier.testTag("switch_background_processing")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "When enabled, catalog analysis, chunk splitting, AI product extraction, Excel/CSV generation, and Drive uploads will continue uninterrupted with foreground service notifications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Theme Settings Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_settings_theme"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Theme Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "App Theme",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                ThemeOptionRow(
                    label = "System Default",
                    icon = Icons.Default.Smartphone,
                    selected = currentTheme == AppThemeMode.SYSTEM,
                    onClick = { onThemeChange(AppThemeMode.SYSTEM) },
                    testTag = "theme_system_radio"
                )

                ThemeOptionRow(
                    label = "Light Theme",
                    icon = Icons.Default.LightMode,
                    selected = currentTheme == AppThemeMode.LIGHT,
                    onClick = { onThemeChange(AppThemeMode.LIGHT) },
                    testTag = "theme_light_radio"
                )

                ThemeOptionRow(
                    label = "Dark Theme",
                    icon = Icons.Default.DarkMode,
                    selected = currentTheme == AppThemeMode.DARK,
                    onClick = { onThemeChange(AppThemeMode.DARK) },
                    testTag = "theme_dark_radio"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Regex Validation Config Dialog
    if (showRegexDialog) {
        RegexValidationConfigDialog(
            rules = validationRules,
            onSaveRules = onSaveValidationRules,
            onResetDefaults = onResetValidationRules,
            onDismiss = { showRegexDialog = false }
        )
    }

    // Schema Manager Dialog
    if (showSchemaManagerDialog) {
        SchemaManagerDialog(
            schemas = allSchemas,
            activeSchemaId = activeSchemaId,
            onSetActiveSchema = onSetActiveSchema,
            onSaveSchema = onSaveSchema,
            onDeleteSchema = onDeleteSchema,
            onResetDefaults = onResetSchemasDefaults,
            onDismiss = { showSchemaManagerDialog = false }
        )
    }
}

@Composable
fun ThemeOptionRow(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

