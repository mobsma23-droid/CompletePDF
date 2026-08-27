package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppThemeMode
import com.example.model.DefaultSchemas
import com.example.model.DefaultValidationRules
import com.example.model.ExtractionSchema
import com.example.model.FieldValidationRule
import com.example.service.GoogleDriveUploader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "app_settings")

class SettingsRepository(private val context: Context) {

    private val jsonHelper = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    companion object {
        val KEY_THEME = stringPreferencesKey("app_theme")
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KEY_DRIVE_OAUTH_TOKEN = stringPreferencesKey("drive_oauth_token")
        val KEY_DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        val KEY_DRIVE_AUTO_UPLOAD = booleanPreferencesKey("drive_auto_upload_enabled")
        val KEY_BACKGROUND_PROCESSING = booleanPreferencesKey("background_processing_enabled")
        val KEY_VALIDATION_RULES = stringPreferencesKey("custom_regex_validation_rules_json")
        val KEY_SAVED_SCHEMAS = stringPreferencesKey("saved_extraction_schemas_json")
        val KEY_ACTIVE_SCHEMA_ID = stringPreferencesKey("active_extraction_schema_id")
    }

    val themeModeFlow: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        val saved = prefs[KEY_THEME] ?: AppThemeMode.SYSTEM.name
        try {
            AppThemeMode.valueOf(saved)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    val geminiApiKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_GEMINI_API_KEY] ?: ""
    }

    val driveOAuthTokenFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DRIVE_OAUTH_TOKEN] ?: ""
    }

    val driveFolderIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DRIVE_FOLDER_ID] ?: GoogleDriveUploader.FOLDER_ID
    }

    val driveAutoUploadFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DRIVE_AUTO_UPLOAD] ?: true
    }

    val backgroundProcessingFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BACKGROUND_PROCESSING] ?: true
    }

    val validationRulesFlow: Flow<List<FieldValidationRule>> = context.dataStore.data.map { prefs ->
        val rawJson = prefs[KEY_VALIDATION_RULES]
        if (rawJson.isNullOrBlank()) {
            DefaultValidationRules.getDefaultRules()
        } else {
            try {
                jsonHelper.decodeFromString<List<FieldValidationRule>>(rawJson)
            } catch (e: Exception) {
                DefaultValidationRules.getDefaultRules()
            }
        }
    }

    val activeSchemaIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_SCHEMA_ID] ?: DefaultSchemas.ID_SUPERMARKET
    }

    val allSchemasFlow: Flow<List<ExtractionSchema>> = context.dataStore.data.map { prefs ->
        val presets = DefaultSchemas.getAllPresets()
        val customJson = prefs[KEY_SAVED_SCHEMAS]
        val customList = if (!customJson.isNullOrBlank()) {
            try {
                jsonHelper.decodeFromString<List<ExtractionSchema>>(customJson)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val activeId = prefs[KEY_ACTIVE_SCHEMA_ID] ?: DefaultSchemas.ID_SUPERMARKET

        // Merge presets + custom, applying the active default flag
        (presets + customList).map { schema ->
            schema.copy(isDefault = (schema.id == activeId))
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME] = mode.name
        }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GEMINI_API_KEY] = key.trim()
        }
    }

    suspend fun setDriveOAuthToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DRIVE_OAUTH_TOKEN] = token.trim()
        }
    }

    suspend fun setDriveFolderId(folderId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DRIVE_FOLDER_ID] = folderId.trim().ifBlank { GoogleDriveUploader.FOLDER_ID }
        }
    }

    suspend fun setDriveAutoUpload(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DRIVE_AUTO_UPLOAD] = enabled
        }
    }

    suspend fun setBackgroundProcessing(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BACKGROUND_PROCESSING] = enabled
        }
    }

    suspend fun saveValidationRules(rules: List<FieldValidationRule>) {
        context.dataStore.edit { prefs ->
            val json = jsonHelper.encodeToString(rules)
            prefs[KEY_VALIDATION_RULES] = json
        }
    }

    suspend fun resetValidationRulesToDefault() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_VALIDATION_RULES)
        }
    }

    suspend fun setActiveSchemaId(schemaId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_SCHEMA_ID] = schemaId
        }
    }

    suspend fun saveCustomSchema(schema: ExtractionSchema) {
        context.dataStore.edit { prefs ->
            val existingJson = prefs[KEY_SAVED_SCHEMAS]
            val currentCustom = if (!existingJson.isNullOrBlank()) {
                try {
                    jsonHelper.decodeFromString<List<ExtractionSchema>>(existingJson).toMutableList()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }

            val existingIndex = currentCustom.indexOfFirst { it.id == schema.id }
            if (existingIndex >= 0) {
                currentCustom[existingIndex] = schema.copy(isPreset = false)
            } else {
                currentCustom.add(schema.copy(isPreset = false))
            }

            prefs[KEY_SAVED_SCHEMAS] = jsonHelper.encodeToString(currentCustom)
        }
    }

    suspend fun deleteCustomSchema(schemaId: String) {
        context.dataStore.edit { prefs ->
            val existingJson = prefs[KEY_SAVED_SCHEMAS]
            if (!existingJson.isNullOrBlank()) {
                try {
                    val currentCustom = jsonHelper.decodeFromString<List<ExtractionSchema>>(existingJson)
                    val updated = currentCustom.filter { it.id != schemaId }
                    prefs[KEY_SAVED_SCHEMAS] = jsonHelper.encodeToString(updated)

                    // If active schema was deleted, reset to supermarket preset
                    if (prefs[KEY_ACTIVE_SCHEMA_ID] == schemaId) {
                        prefs[KEY_ACTIVE_SCHEMA_ID] = DefaultSchemas.ID_SUPERMARKET
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    suspend fun resetSchemasToDefault() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SAVED_SCHEMAS)
            prefs[KEY_ACTIVE_SCHEMA_ID] = DefaultSchemas.ID_SUPERMARKET
        }
    }
}
