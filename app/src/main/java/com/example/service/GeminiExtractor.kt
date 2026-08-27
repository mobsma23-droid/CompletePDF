package com.example.service

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.model.DefaultSchemas
import com.example.model.ExtractionSchema
import com.example.model.ProductItem
import com.example.model.SchemaFieldType
import com.example.utils.PdfChunkInfo
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object GeminiExtractor {
    private const val TAG = "GeminiExtractor"
    
    // Primary model and fallback list
    private const val MODEL_NAME = "gemini-2.5-flash"
    private val MODEL_FALLBACKS = listOf(
        "gemini-2.5-flash",
        "gemini-3.5-flash",
        "gemini-3.6-flash",
        "gemini-2.5-pro",
        "gemini-flash-latest",
        "gemini-flash-lite-latest",
        "gemini-3.1-pro-preview",
        "gemini-pro-latest"
    )

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Builds a tailored AI extraction prompt according to the given ExtractionSchema.
     */
    fun buildPromptForSchema(schema: ExtractionSchema): String {
        val fieldsDesc = StringBuilder()
        val exampleObj = org.json.JSONObject()

        schema.fields.forEachIndexed { index, field ->
            val reqNote = if (field.required) "(Required)" else "(Optional - empty string \"\" if not found)"
            fieldsDesc.append("${index + 1}. \"${field.name}\" [${field.label}]: ${field.description} $reqNote\n")

            when (field.type) {
                SchemaFieldType.PRICE -> exampleObj.put(field.name, "45.50")
                SchemaFieldType.NUMBER -> exampleObj.put(field.name, "10")
                SchemaFieldType.DATE -> exampleObj.put(field.name, "01 Aug - 15 Aug 2024")
                SchemaFieldType.CATEGORY -> exampleObj.put(field.name, "General")
                SchemaFieldType.BOOLEAN -> exampleObj.put(field.name, "true")
                SchemaFieldType.STRING -> {
                    when {
                        field.name.contains("SKU", ignoreCase = true) -> exampleObj.put(field.name, "SKU-8921")
                        field.name.contains("Brand", ignoreCase = true) -> exampleObj.put(field.name, "BrandName")
                        field.name.contains("Produit", ignoreCase = true) || field.name.contains("name", ignoreCase = true) -> exampleObj.put(field.name, "Sample Product Item")
                        field.name.contains("Barcode", ignoreCase = true) -> exampleObj.put(field.name, "7891029384")
                        field.name.contains("unite", ignoreCase = true) -> exampleObj.put(field.name, "1L")
                        else -> exampleObj.put(field.name, "Sample Value")
                    }
                }
            }
        }

        val sampleJsonArray = org.json.JSONArray().put(exampleObj).toString(2)

        return """
You are a precise data extraction AI specializing in extracting commercial items from PDF catalogs, flyers, price lists, and document brochures.

Your objective is to extract all items from the document into a strict, valid JSON array of objects following the exact schema: "${schema.name}".

OUTPUT SCHEMA REQUIREMENTS:
$fieldsDesc
DATA CLEANING & NUMERIC RULES:
- For Price / Numeric fields: Strip currency symbols ($, €, £, Rs, MUR, USD, EUR), remove thousand commas/spaces, and always use a single dot '.' for decimals (e.g., convert "Rs 1,250.50" or "1 250,50" to "1250.50").
- For missing or unstated optional fields: Default strictly to an empty string "". Never output null, "N/A", "Unknown", or "None".
- Clean all text values by trimming extra leading/trailing whitespace.
- Do not hallucinate data not present in the document.

JSON FORMAT ENFORCEMENT:
- Return ONLY a valid JSON array containing objects adhering to the schema.
- Do NOT wrap in Markdown code blocks. Return raw JSON text.

EXAMPLE VALID JSON OUTPUT:
$sampleJsonArray
""".trimIndent()
    }

    /**
     * Extracts product list from a single PDF chunk with full crash and timeout protection.
     */
    suspend fun extractFromChunk(
        chunk: PdfChunkInfo,
        apiKeyOverride: String? = null,
        schema: ExtractionSchema = DefaultSchemas.getDefaultSchema()
    ): List<ProductItem> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride?.ifBlank { null } ?: BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API key is not configured. Please set your key in Settings.")
        }

        val chunkBytes = chunk.bytes
        if (chunkBytes.isEmpty()) {
            Log.w(TAG, "Chunk ${chunk.chunkIndex} has empty bytes, skipping.")
            return@withContext emptyList()
        }

        val prompt = buildPromptForSchema(schema)
        var jsonResponseText: String? = null

        // 1. Try Google GenAI SDK first
        try {
            Log.d(TAG, "Attempting SDK extraction for chunk ${chunk.chunkIndex}/${chunk.totalChunks} (${chunkBytes.size} bytes)...")
            val generativeModel = GenerativeModel(
                modelName = MODEL_NAME,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    temperature = 0.1f
                }
            )

            val pdfContent = content {
                blob("application/pdf", chunkBytes)
                text(prompt)
            }

            val response = generativeModel.generateContent(pdfContent)
            jsonResponseText = response.text
            Log.d(TAG, "SDK response received (${jsonResponseText?.length ?: 0} chars)")
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError in GenAI SDK: ${e.message}")
            System.gc()
        } catch (t: Throwable) {
            Log.w(TAG, "SDK extraction unavailable or failed (${t.message}). Falling back to direct REST API...", t)
        }

        // 2. Direct REST API fallback if SDK failed or returned empty text
        if (jsonResponseText.isNullOrBlank()) {
            try {
                jsonResponseText = callGeminiRestApiWithRetries(chunkBytes, apiKey, prompt)
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OutOfMemoryError during REST API call: ${e.message}")
                System.gc()
                throw IllegalStateException("PDF chunk is too large for device memory limit.")
            } catch (e: Exception) {
                Log.e(TAG, "All REST API attempts failed: ${e.message}")
                throw e
            }
        }

        if (jsonResponseText.isNullOrBlank()) {
            Log.w(TAG, "Empty response received from Gemini for chunk ${chunk.chunkIndex}")
            return@withContext emptyList()
        }

        // Parse extracted JSON string into List<ProductItem> conforming to schema
        try {
            return@withContext parseProductsJson(jsonResponseText, schema)
        } catch (e: Exception) {
            Log.e(TAG, "JSON parsing error on chunk ${chunk.chunkIndex}: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    private suspend fun callGeminiRestApiWithRetries(
        pdfBytes: ByteArray,
        apiKey: String,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val base64Pdf: String
        try {
            base64Pdf = Base64.encodeToString(pdfBytes, Base64.NO_WRAP)
        } catch (oom: OutOfMemoryError) {
            System.gc()
            throw IllegalStateException("Device ran out of memory while encoding PDF data for upload.")
        }
        
        var lastException: Exception? = null

        for (model in MODEL_FALLBACKS) {
            var attempt = 0
            val maxRetries = 2
            
            while (attempt <= maxRetries) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

                val requestJson = org.json.JSONObject().apply {
                    put("contents", org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("parts", org.json.JSONArray().apply {
                                put(org.json.JSONObject().apply {
                                    put("text", prompt)
                                })
                                put(org.json.JSONObject().apply {
                                    put("inlineData", org.json.JSONObject().apply {
                                        put("mimeType", "application/pdf")
                                        put("data", base64Pdf)
                                    })
                                })
                            })
                        })
                    })
                    put("generationConfig", org.json.JSONObject().apply {
                        put("responseMimeType", "application/json")
                        put("temperature", 0.1)
                    })
                }

                val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                try {
                    val resultText = okHttpClient.newCall(request).execute().use { response ->
                        val resBody = response.body?.string() ?: ""
                        val code = response.code

                        if (code == 429 || code == 503 || code == 500) {
                            // Transient rate limit or server error - retry with backoff
                            Log.w(TAG, "Model $model returned HTTP $code (attempt $attempt). Backing off...")
                            throw IOException("HTTP $code temporary failure: $resBody")
                        }

                        if (!response.isSuccessful) {
                            Log.w(TAG, "Model $model REST call failed ($code): $resBody")
                            throw IllegalStateException("Gemini API REST call failed ($code): $resBody")
                        }

                        val jsonObject = org.json.JSONObject(resBody)
                        val candidates = jsonObject.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                        val contentObj = firstCandidate?.optJSONObject("content")
                        val partsArray = contentObj?.optJSONArray("parts")
                        partsArray?.optJSONObject(0)?.optString("text") ?: ""
                    }

                    if (resultText.isNotBlank()) {
                        Log.i(TAG, "Successfully extracted content using Gemini model: $model")
                        return@withContext resultText
                    }
                } catch (e: SocketTimeoutException) {
                    Log.w(TAG, "SocketTimeout on model $model (attempt $attempt): ${e.message}")
                    lastException = e
                    attempt++
                    if (attempt <= maxRetries) {
                        delay(1000L * attempt)
                    }
                } catch (e: UnknownHostException) {
                    Log.w(TAG, "Network connection unavailable: ${e.message}")
                    throw IllegalStateException("Network unreachable. Please check your internet connection.")
                } catch (e: IOException) {
                    Log.w(TAG, "Transient I/O error on model $model (attempt $attempt): ${e.message}")
                    lastException = e
                    attempt++
                    if (attempt <= maxRetries) {
                        delay(1500L * attempt)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Model $model failed during REST call: ${e.message}. Trying next fallback...")
                    lastException = e
                    break // break retry loop to try next model fallback
                }
            }
        }

        throw lastException ?: IllegalStateException("All Gemini model fallbacks failed to process the document chunk.")
    }

    private fun parseProductsJson(rawJson: String, schema: ExtractionSchema): List<ProductItem> {
        val cleanedJson = sanitizeJsonString(rawJson)
        val results = mutableListOf<ProductItem>()

        // 1. Try Kotlinx Serialization
        try {
            val jsonElement = jsonParser.parseToJsonElement(cleanedJson)
            val array = when {
                jsonElement is kotlinx.serialization.json.JsonArray -> jsonElement
                jsonElement is kotlinx.serialization.json.JsonObject && jsonElement.containsKey("products") -> {
                    jsonElement["products"]?.jsonArray
                }
                jsonElement is kotlinx.serialization.json.JsonObject && jsonElement.containsKey("items") -> {
                    jsonElement["items"]?.jsonArray
                }
                jsonElement is kotlinx.serialization.json.JsonObject && jsonElement.containsKey("data") -> {
                    jsonElement["data"]?.jsonArray
                }
                else -> null
            }

            if (array != null) {
                for (item in array) {
                    if (item is kotlinx.serialization.json.JsonObject) {
                        val parsedItem = buildProductItemFromJsonObject(item, schema)
                        if (parsedItem.Produit.isNotBlank() && parsedItem.Produit != "N/A") {
                            results.add(parsedItem)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON with Kotlinx Serialization, trying JSONArray fallback: ${e.message}")
        }

        if (results.isNotEmpty()) {
            return results
        }

        // 2. Try org.json.JSONArray fallback
        try {
            val jsonArray = JSONArray(cleanedJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i)
                if (obj != null) {
                    val parsedItem = buildProductItemFromOrgJson(obj, schema)
                    if (parsedItem.Produit.isNotBlank() && parsedItem.Produit != "N/A") {
                        results.add(parsedItem)
                    }
                }
            }
        } catch (e2: Exception) {
            Log.w(TAG, "JSONArray parsing failed: ${e2.message}. Attempting regex object recovery...")
        }

        if (results.isNotEmpty()) {
            return results
        }

        // 3. Resilient fallback: Extract all individual JSON objects via Regex
        val regexResults = extractObjectsWithRegex(rawJson, schema)
        Log.i(TAG, "Regex object extractor recovered ${regexResults.size} product items from raw response")
        return regexResults
    }

    private fun buildProductItemFromJsonObject(
        obj: kotlinx.serialization.json.JsonObject,
        schema: ExtractionSchema
    ): ProductItem {
        fun getStringValue(vararg keys: String): String {
            for (k in keys) {
                val elem = obj[k] ?: obj[k.lowercase()] ?: obj[k.uppercase()]
                if (elem != null) {
                    return try {
                        elem.jsonPrimitive.content.trim()
                    } catch (e: Exception) {
                        elem.toString().trim().removeSurrounding("\"")
                    }
                }
            }
            return ""
        }

        val produit = getStringValue("Produit", "produit", "product", "name", "item", "title", "description")
        val brand = getStringValue("Brand", "brand", "marque", "manufacturer")
        val category = getStringValue("Category", "category", "categorie", "section", "department")
        val prix = getStringValue("Prix", "prix", "price", "promo_price", "selling_price", "prix_promo", "unit_price")
        val prixNormal = getStringValue("Prix_Normal", "prix_normal", "normal_price", "regular_price", "old_price", "msrp")
        val datePromo = getStringValue("Date_Promo", "date_promo", "promo_date", "validity_date", "valid_until")
        val unite = getStringValue("unité", "unite", "unit", "size", "format")
        val sku = getStringValue("SKU", "sku", "item_code", "article_number", "model_number", "part_number", "code")
        val barcode = getStringValue("Barcode", "barcode", "ean", "upc")

        val customFields = mutableMapOf<String, String>()
        for (field in schema.fields) {
            val key = field.name
            if (key !in listOf("Produit", "Brand", "Category", "Prix", "Prix_Normal", "Date_Promo", "unité", "SKU", "Barcode")) {
                val value = getStringValue(key)
                if (value.isNotBlank()) {
                    customFields[key] = value
                }
            }
        }

        for ((k, v) in obj) {
            if (k !in listOf("Produit", "produit", "Brand", "brand", "Category", "category", "Prix", "prix", "Prix_Normal", "prix_normal", "Date_Promo", "date_promo", "unité", "unite", "SKU", "sku", "Barcode", "barcode")) {
                try {
                    customFields[k] = v.jsonPrimitive.content.trim()
                } catch (e: Exception) {
                    // Ignore non-primitive
                }
            }
        }

        return ProductItem(
            Produit = produit,
            Brand = brand,
            Category = category,
            Prix = prix,
            Prix_Normal = prixNormal,
            Date_Promo = datePromo,
            unité = unite,
            SKU = sku,
            Barcode = barcode,
            customFields = customFields
        ).sanitized()
    }

    private fun buildProductItemFromOrgJson(
        obj: org.json.JSONObject,
        schema: ExtractionSchema
    ): ProductItem {
        fun getString(vararg keys: String): String {
            for (k in keys) {
                if (obj.has(k)) return obj.optString(k, "").trim()
                if (obj.has(k.lowercase())) return obj.optString(k.lowercase(), "").trim()
                if (obj.has(k.uppercase())) return obj.optString(k.uppercase(), "").trim()
            }
            return ""
        }

        val produit = getString("Produit", "produit", "product", "name", "item")
        val brand = getString("Brand", "brand", "marque")
        val category = getString("Category", "category", "section")
        val prix = getString("Prix", "prix", "price", "promo_price")
        val prixNormal = getString("Prix_Normal", "prix_normal", "normal_price", "regular_price", "old_price")
        val datePromo = getString("Date_Promo", "date_promo", "promo_date", "validity_date")
        val unite = getString("unité", "unite", "unit")
        val sku = getString("SKU", "sku", "item_code", "model_number")
        val barcode = getString("Barcode", "barcode", "ean", "upc")

        val customFields = mutableMapOf<String, String>()
        for (field in schema.fields) {
            val key = field.name
            if (key !in listOf("Produit", "Brand", "Category", "Prix", "Prix_Normal", "Date_Promo", "unité", "SKU", "Barcode")) {
                val value = getString(key)
                if (value.isNotBlank()) {
                    customFields[key] = value
                }
            }
        }

        return ProductItem(
            Produit = produit,
            Brand = brand,
            Category = category,
            Prix = prix,
            Prix_Normal = prixNormal,
            Date_Promo = datePromo,
            unité = unite,
            SKU = sku,
            Barcode = barcode,
            customFields = customFields
        ).sanitized()
    }

    private fun sanitizeJsonString(rawJson: String): String {
        var s = rawJson.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        // Fix double closing braces before array end, e.g. "}\n}\n]" or "}\n\s*}"
        s = s.replace(Regex("""\}\s*\}\s*\]"""), "}\n]")
        s = s.replace(Regex("""\}\s*\}\s*$"""), "}\n]")

        // Remove trailing commas before closing braces/brackets
        s = s.replace(Regex(""",\s*\]"""), "]")
        s = s.replace(Regex(""",\s*\}"""), "}")

        // Ensure array bracket closure if array is truncated
        if (s.startsWith("[") && !s.contains("]")) {
            s = if (s.trimEnd().endsWith("}")) "$s]" else "$s}]"
        }
        return s
    }

    private fun extractObjectsWithRegex(rawJson: String, schema: ExtractionSchema): List<ProductItem> {
        val results = mutableListOf<ProductItem>()
        val objectRegex = Regex("""\{[^{}]*?\}""", RegexOption.DOT_MATCHES_ALL)
        val matches = objectRegex.findAll(rawJson)

        for (match in matches) {
            try {
                val obj = org.json.JSONObject(match.value)
                val item = buildProductItemFromOrgJson(obj, schema)
                if (item.Produit.isNotBlank() && item.Produit != "N/A") {
                    results.add(item)
                }
            } catch (e: Exception) {
                // Skip individual invalid items
            }
        }
        return results
    }

    /**
     * Consolidates and merges extracted product items from multiple chunks.
     */
    fun mergeChunkResults(allChunkProducts: List<List<ProductItem>>): List<ProductItem> {
        val flattened = allChunkProducts.flatten()
        val uniqueProducts = mutableListOf<ProductItem>()
        val seenKeys = mutableSetOf<String>()

        for (product in flattened) {
            val key = if (product.SKU.isNotBlank()) {
                "${product.Produit.lowercase()}_${product.SKU.lowercase()}"
            } else {
                "${product.Produit.lowercase()}_${product.Brand.lowercase()}_${product.Prix}"
            }
            if (seenKeys.add(key)) {
                uniqueProducts.add(product)
            }
        }

        return uniqueProducts
    }
}
