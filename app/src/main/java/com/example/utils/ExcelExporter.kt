package com.example.utils

import android.util.Log
import com.example.model.ExtractionSchema
import com.example.model.ProductItem
import com.example.model.SchemaFieldType
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Utility class to export product data to Excel (.xlsx) format with dynamic schema support
 * and bulletproof error handling for cell length limits and special characters.
 */
class ExcelExporter {
    companion object {
        private const val TAG = "ExcelExporter"
        private const val MAX_CELL_LENGTH = 32000 // Apache POI cell limit safety boundary
        val DEFAULT_HEADERS = listOf("Produit", "Brand", "Category", "Prix Promo", "Prix Normal", "Date Promo", "unité")

        /**
         * Exports a list of ProductItem to an Excel (.xlsx) file at the given output path.
         */
        fun export(products: List<ProductItem>, outputPath: String, schema: ExtractionSchema? = null): File {
            val file = File(outputPath)
            file.parentFile?.mkdirs()
            generateExcel(file, products, schema)
            return file
        }

        /**
         * Exports a list of ProductItem to an Excel (.xlsx) file.
         */
        fun export(products: List<ProductItem>, outputFile: File, schema: ExtractionSchema? = null): File {
            outputFile.parentFile?.mkdirs()
            generateExcel(outputFile, products, schema)
            return outputFile
        }

        private fun generateExcel(file: File, products: List<ProductItem>, schema: ExtractionSchema?) {
            var workbook: XSSFWorkbook? = null
            try {
                workbook = XSSFWorkbook()
                val sheet = workbook.createSheet("Extracted Data")

                // Header styling
                val headerFont = workbook.createFont().apply {
                    bold = true
                    color = IndexedColors.WHITE.getIndex()
                    fontHeightInPoints = 11.toShort()
                }

                val headerStyle = workbook.createCellStyle().apply {
                    setFont(headerFont)
                    fillForegroundColor = IndexedColors.ROYAL_BLUE.getIndex()
                    fillPattern = FillPatternType.SOLID_FOREGROUND
                    alignment = HorizontalAlignment.CENTER
                }

                val dataStyle = workbook.createCellStyle().apply {
                    alignment = HorizontalAlignment.LEFT
                }

                val priceStyle = workbook.createCellStyle().apply {
                    alignment = HorizontalAlignment.RIGHT
                }

                val headers: List<String>
                val fieldKeys: List<String>
                val isNumericField: List<Boolean>

                if (schema != null && schema.fields.isNotEmpty()) {
                    headers = schema.fields.map { it.label.ifBlank { it.name } }
                    fieldKeys = schema.fields.map { it.name }
                    isNumericField = schema.fields.map { it.type == SchemaFieldType.PRICE || it.type == SchemaFieldType.NUMBER }
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
                    isNumericField = baseKeys.map { it.contains("prix", ignoreCase = true) || it.contains("price", ignoreCase = true) }
                }

                // Header row
                val headerRow = sheet.createRow(0)
                headers.forEachIndexed { colIndex, header ->
                    val cell = headerRow.createCell(colIndex)
                    cell.setCellValue(header.take(MAX_CELL_LENGTH))
                    cell.cellStyle = headerStyle
                }

                // Data rows
                products.forEachIndexed { rowIndex, product ->
                    val row = sheet.createRow(rowIndex + 1)

                    fieldKeys.forEachIndexed { colIndex, key ->
                        val cell = row.createCell(colIndex)
                        val rawValue = product.getFieldValue(key)
                        val safeValue = rawValue.take(MAX_CELL_LENGTH)
                        val isNum = isNumericField.getOrElse(colIndex) { false }

                        if (isNum) {
                            val numVal = safeValue.toDoubleOrNull()
                            if (numVal != null && !numVal.isNaN() && !numVal.isInfinite()) {
                                cell.setCellValue(numVal)
                            } else {
                                cell.setCellValue(safeValue)
                            }
                            cell.cellStyle = priceStyle
                        } else {
                            cell.setCellValue(safeValue)
                            cell.cellStyle = dataStyle
                        }
                    }
                }

                // Column width adjustments
                for (i in headers.indices) {
                    try {
                        sheet.setColumnWidth(i, 22 * 256)
                    } catch (e: Exception) {
                        // ignore column width errors
                    }
                }

                FileOutputStream(file).use { fos ->
                    BufferedOutputStream(fos).use { bos ->
                        workbook.write(bos)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate Excel file: ${e.message}", e)
                throw e
            } finally {
                try {
                    workbook?.close()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }
}
