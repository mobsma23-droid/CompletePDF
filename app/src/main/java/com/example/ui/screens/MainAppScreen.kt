package com.example.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.model.AppThemeMode
import com.example.model.QueueExecutionState
import com.example.ui.components.TopNavBar
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel

@Composable
fun MainAppScreen(
    viewModel: MainViewModel
) {
    val authState by viewModel.authState.collectAsState()
    val currentTheme by viewModel.themeMode.collectAsState()
    val tasks by viewModel.pdfTasks.collectAsState()
    val queueState by viewModel.queueExecutionState.collectAsState()
    val isProcessing = queueState == QueueExecutionState.PROCESSING
    val allProducts by viewModel.allExtractedProducts.collectAsState()
    val savedFiles by viewModel.savedFiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val driveToken by viewModel.driveOAuthToken.collectAsState()
    val driveFolderId by viewModel.driveFolderId.collectAsState()
    val driveAutoUpload by viewModel.driveAutoUpload.collectAsState()
    val backgroundProcessingEnabled by viewModel.backgroundProcessingEnabled.collectAsState()
    val validationRules by viewModel.validationRules.collectAsState()
    val allSchemas by viewModel.allSchemas.collectAsState()
    val activeSchemaId by viewModel.activeSchemaId.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val darkThemeActive = when (currentTheme) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MyApplicationTheme(darkTheme = darkThemeActive) {
        if (authState !is com.example.service.AuthState.Authenticated) {
            LoginScreen(
                authState = authState,
                onSignInClick = { viewModel.signInWithGoogle() },
                onClearError = { viewModel.clearAuthError() }
            )
        } else {
            val currentUser = (authState as com.example.service.AuthState.Authenticated).user

            Scaffold(
                topBar = {
                    TopNavBar(
                        currentTheme = currentTheme,
                        onToggleTheme = {
                            val next = when (currentTheme) {
                                AppThemeMode.SYSTEM -> AppThemeMode.LIGHT
                                AppThemeMode.LIGHT -> AppThemeMode.DARK
                                AppThemeMode.DARK -> AppThemeMode.SYSTEM
                            }
                            viewModel.setThemeMode(next)
                        },
                        onOpenSettings = { selectedTabIndex = 4 }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Extractor") },
                            label = { Text("Extractor") },
                            modifier = Modifier.testTag("tab_extractor")
                        )

                        NavigationBarItem(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            icon = { Icon(Icons.Default.Assessment, contentDescription = "Dashboard") },
                            label = { Text("Dashboard") },
                            modifier = Modifier.testTag("tab_dashboard")
                        )

                        NavigationBarItem(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            icon = { Icon(Icons.Default.Store, contentDescription = "Supermarkets") },
                            label = { Text("Flyers") },
                            modifier = Modifier.testTag("tab_supermarkets")
                        )

                        NavigationBarItem(
                            selected = selectedTabIndex == 3,
                            onClick = { selectedTabIndex = 3 },
                            icon = { Icon(Icons.Default.TableChart, contentDescription = "Catalog") },
                            label = { Text("Catalog") },
                            modifier = Modifier.testTag("tab_catalog")
                        )

                        NavigationBarItem(
                            selected = selectedTabIndex == 4,
                            onClick = { selectedTabIndex = 4 },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") },
                            modifier = Modifier.testTag("tab_settings")
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTabIndex) {
                        0 -> ExtractorScreen(
                            tasks = tasks,
                            queueState = queueState,
                            activeSchemaId = activeSchemaId,
                            allSchemas = allSchemas,
                            onSelectFiles = { uris -> viewModel.addPdfUris(uris) },
                            onRemoveTask = { id -> viewModel.removeTask(id) },
                            onClearAll = { viewModel.clearAllTasks() },
                            onStartQueue = { viewModel.startOrResumeQueue() },
                            onPauseQueue = { viewModel.pauseQueue() },
                            onRetryTask = { id -> viewModel.retryTask(id) },
                            onRetryAllFailed = { viewModel.retryAllFailed() },
                            onClearCompleted = { viewModel.clearCompletedTasks() },
                            onMoveTaskUp = { id -> viewModel.moveTaskUp(id) },
                            onMoveTaskDown = { id -> viewModel.moveTaskDown(id) },
                            onAssignTaskSchema = { taskId, schemaId -> viewModel.assignSchemaToTask(taskId, schemaId) },
                            onSetActiveSchema = { schemaId -> viewModel.setActiveSchema(schemaId) },
                            onSaveSchema = { schema -> viewModel.saveCustomSchema(schema) },
                            onDeleteSchema = { schemaId -> viewModel.deleteCustomSchema(schemaId) },
                            onResetSchemasDefaults = { viewModel.resetSchemasToDefault() },
                            onBatchDelete = { ids -> viewModel.batchDeleteTasks(ids) },
                            onBatchUploadToDrive = { ids -> viewModel.batchUploadToDrive(ids) },
                            onBulkRename = { map -> viewModel.bulkRenameTasks(map) }
                        )

                        1 -> StatusDashboardScreen(
                            savedFiles = savedFiles,
                            activeTasks = tasks,
                            isProcessingBatch = isProcessing,
                            onDeleteFile = { id -> viewModel.deleteSavedFile(id) },
                            onClearAllHistory = { viewModel.clearAllSavedFiles() },
                            onLoadProductsForFile = { id -> viewModel.getProductsForFile(id) }
                        )

                        2 -> SupermarketsScreen()

                        3 -> CatalogScreen(
                            allProducts = allProducts,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { q -> viewModel.setSearchQuery(q) },
                            selectedCategory = selectedCategory,
                            onCategorySelect = { c -> viewModel.setSelectedCategory(c) }
                        )

                        4 -> SettingsScreen(
                            currentTheme = currentTheme,
                            onThemeChange = { mode -> viewModel.setThemeMode(mode) },
                            driveToken = driveToken,
                            onDriveTokenSave = { t -> viewModel.setDriveOAuthToken(t) },
                            driveFolderId = driveFolderId,
                            onDriveFolderIdSave = { f -> viewModel.setDriveFolderId(f) },
                            driveAutoUpload = driveAutoUpload,
                            onDriveAutoUploadChange = { enabled -> viewModel.setDriveAutoUpload(enabled) },
                            backgroundProcessingEnabled = backgroundProcessingEnabled,
                            onBackgroundProcessingChange = { enabled -> viewModel.setBackgroundProcessing(enabled) },
                            validationRules = validationRules,
                            onSaveValidationRules = { rules -> viewModel.saveValidationRules(rules) },
                            onResetValidationRules = { viewModel.resetValidationRulesToDefault() },
                            allSchemas = allSchemas,
                            activeSchemaId = activeSchemaId,
                            onSetActiveSchema = { schemaId -> viewModel.setActiveSchema(schemaId) },
                            onSaveSchema = { schema -> viewModel.saveCustomSchema(schema) },
                            onDeleteSchema = { schemaId -> viewModel.deleteCustomSchema(schemaId) },
                            onResetSchemasDefaults = { viewModel.resetSchemasToDefault() },
                            currentUser = currentUser,
                            onSignOut = { viewModel.signOut() }
                        )
                    }
                }
            }
        }
    }
}
