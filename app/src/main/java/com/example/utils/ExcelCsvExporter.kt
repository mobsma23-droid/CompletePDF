package com.example.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.model.ExtractionSchema
import com.example.model.ProductItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ExportResult(
    val xlsxFile: File,
    val csvFile: File
)

object ExcelCsvExporter {
    private const val TAG = "ExcelCsvExporter"

    suspend fun exportProducts(
        context: Context,
        originalPdfName: String,
        products: List<ProductItem>,
        schema: ExtractionSchema? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        val baseName = sanitizeFileName(originalPdfName.removeSuffix(".pdf"))
        val exportDir = getExportDirectory(context)

        val xlsxFile = File(exportDir, "${baseName}_extracted.xlsx")
        val csvFile = File(exportDir, "${baseName}_extracted.csv")

        ExcelExporter.export(products, xlsxFile, schema)
        CsvExporter.export(products, csvFile, schema)

        Log.i(TAG, "Exported ${products.size} items to ${xlsxFile.name} and ${csvFile.name} with schema ${schema?.name ?: "default"}")
        ExportResult(xlsxFile = xlsxFile, csvFile = csvFile)
    }

    private fun getExportDirectory(context: Context): File {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "ExtractedCatalogs"
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(50)
    }
}
