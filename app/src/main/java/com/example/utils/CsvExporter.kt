package com.example.utils

import android.util.Log
import com.example.model.ExtractionSchema
import com.example.model.ProductItem
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Utility class to export product data to CSV format with dynamic schema support
 * and bulletproof error handling.
 */
class CsvExporter {
    companion object {
        private const val TAG = "CsvExporter"
        val DEFAULT_HEADERS = listOf("Produit", "Brand", "Category", "Prix Promo", "Prix Normal", "Date Promo", "unité")

        /**
         * Exports a list of ProductItem to a CSV file at the specified output path.
         */
        fun export(products: List<ProductItem>, outputPath: String, schema: ExtractionSchema? = null): File {
            val file = File(outputPath)
            file.parentFile?.mkdirs()
            generateCsv(file, products, schema)
            return file
        }

        /**
         * Exports a list of ProductItem to a CSV file.
         */
        fun export(products: List<ProductItem>, outputFile: File, schema: ExtractionSchema? = null): File {
            outputFile.parentFile?.mkdirs()
            generateCsv(outputFile, products, schema)
            return outputFile
        }

        private fun generateCsv(file: File, products: List<ProductItem>, schema: ExtractionSchema?) {
            val headers: List<String>
            val fieldKeys: List<String>

            if (schema != null && schema.fields.isNotEmpty()) {
                headers = schema.fields.map { it.label.ifBlank { it.name } }
                fieldKeys = schema.fields.map { it.name }
            } else {
                val customKeys = products.flatMap { it.customFields.keys }.distinct()
                val hasSku = products.any { it.SKU.isNotBlank() }
                val hasBarcode = products.any { it.Barcode.isNotBlank() }

                val baseKeys = mutableListOf("Produit", "Brand", "Category", "Prix", "Prix_Normal", "Date_Promo", "unité")
                val baseHeaders = mutableListOf("Produit", "Brand", "Category", "Prix Promo", "Prix Normal", "Date Promo", "unité")

                if (hasSku) {
                    baseKeys.add(1, "SKU")
                    baseHeaders.add(1, "SKU")
                }
                if (hasBarcode) {
                    baseKeys.add("Barcode")
                    baseHeaders.add("Barcode")
                }
                for (k in customKeys) {
                    if (k !in baseKeys) {
                        baseKeys.add(k)
                        baseHeaders.add(k)
                    }
                }
                headers = baseHeaders
                fieldKeys = baseKeys
            }

            try {
                FileOutputStream(file).use { fos ->
                    OutputStreamWriter(fos, StandardCharsets.UTF_8).use { osw ->
                        BufferedWriter(osw).use { writer ->
                            // Optional UTF-8 BOM for Excel CSV auto-encoding detection
                            writer.write("\uFEFF")

                            // Write Header Row
                            writer.write(headers.joinToString(",") { escapeCsv(it) })
                            writer.newLine()

                            // Write Data Rows
                            products.forEach { product ->
                                val row = fieldKeys.map { key ->
                                    product.getFieldValue(key)
                                }
                                writer.write(row.joinToString(",") { escapeCsv(it) })
                                writer.newLine()
                            }
                            writer.flush()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write CSV file: ${e.message}", e)
                throw e
            }
        }

        private fun escapeCsv(value: String): String {
            val trimmed = value.trim()
            val needsQuotes = trimmed.contains(",") || trimmed.contains("\"") || trimmed.contains("\n") || trimmed.contains("\r")
            return if (needsQuotes) {
                "\"" + trimmed.replace("\"", "\"\"") + "\""
            } else {
                trimmed
            }
        }
    }
}
