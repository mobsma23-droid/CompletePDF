package com.example.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class DriveUploadResult(
    val fileId: String,
    val webViewLink: String?,
    val fileName: String
)

object GoogleDriveUploader {
    private const val TAG = "GoogleDriveUploader"
    const val FOLDER_ID = "1ogyeBvRisQPyUwWt5ZIshfzhYrkfQ9fI"

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Uploads a local file to Google Drive with error isolation and crash protection.
     */
    suspend fun uploadFile(
        file: File,
        mimeType: String,
        oauthToken: String?,
        folderId: String = FOLDER_ID
    ): DriveUploadResult = withContext(Dispatchers.IO) {
        if (oauthToken.isNullOrBlank()) {
            throw IllegalStateException("Google Drive OAuth token is missing. Please authorize or configure your Google Drive token in App Settings.")
        }

        if (!file.exists() || file.length() == 0L) {
            throw IllegalStateException("File '${file.name}' does not exist or is empty.")
        }

        val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink"

        // Metadata JSON
        val metadataJson = JSONObject().apply {
            put("name", file.name)
            put("parents", org.json.JSONArray().apply { put(folderId) })
        }

        val metadataBody = metadataJson.toString()
            .toRequestBody("application/json; charset=UTF-8".toMediaType())

        val fileRequestBody = file.asRequestBody(mimeType.toMediaType())

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(metadataBody)
            .addPart(fileRequestBody)
            .build()

        val request = Request.Builder()
            .url(uploadUrl)
            .addHeader("Authorization", "Bearer $oauthToken")
            .post(multipartBody)
            .build()

        Log.d(TAG, "Uploading ${file.name} to Drive folder $folderId...")

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Drive upload failed code ${response.code}: $responseBody")
                    when (response.code) {
                        401 -> throw IllegalStateException("Google Drive authorization expired or invalid (401). Please update OAuth token in Settings.")
                        403 -> throw IllegalStateException("Google Drive storage quota exceeded or access denied (403).")
                        404 -> throw IllegalStateException("Google Drive destination folder '$folderId' not found (404).")
                        else -> throw IllegalStateException("Google Drive upload error (${response.code}): $responseBody")
                    }
                }

                val json = JSONObject(responseBody)
                val fileId = json.getString("id")
                val webViewLink = json.optString("webViewLink", "https://drive.google.com/file/d/$fileId/view")

                Log.i(TAG, "Successfully uploaded ${file.name} -> ID: $fileId")
                DriveUploadResult(
                    fileId = fileId,
                    webViewLink = webViewLink,
                    fileName = file.name
                )
            }
        } catch (e: UnknownHostException) {
            throw IllegalStateException("Cannot connect to Google Drive: Internet connection unavailable.")
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("Google Drive upload timed out. Please check your network connection and retry.")
        } catch (e: IOException) {
            throw IllegalStateException("Google Drive network error: ${e.message}")
        }
    }
}
