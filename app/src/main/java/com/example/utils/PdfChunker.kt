package com.example.utils

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class PdfChunkInfo(
    val chunkIndex: Int,
    val totalChunks: Int,
    val startPage: Int,
    val endPage: Int,
    val chunkFile: File? = null,
    private val rawBytes: ByteArray? = null
) {
    val bytes: ByteArray
        get() {
            return try {
                rawBytes ?: chunkFile?.let { if (it.exists()) it.readBytes() else ByteArray(0) } ?: ByteArray(0)
            } catch (e: OutOfMemoryError) {
                Log.e("PdfChunkInfo", "OutOfMemoryError reading chunk bytes: ${e.message}")
                System.gc()
                ByteArray(0)
            } catch (e: Exception) {
                Log.e("PdfChunkInfo", "Failed to read chunk bytes: ${e.message}")
                ByteArray(0)
            }
        }

    fun cleanup() {
        try {
            chunkFile?.let {
                if (it.exists()) {
                    it.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup failures
        }
    }
}

object PdfChunker {
    private const val TAG = "PdfChunker"
    private const val MAX_SINGLE_FILE_SIZE = 8 * 1024 * 1024L // 8 MB threshold
    private const val DEFAULT_PAGES_PER_CHUNK = 8
    private const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer

    fun isPdfHeaderValid(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(5)
                val read = input.read(header, 0, 5)
                read == 5 &&
                        header[0] == '%'.code.toByte() &&
                        header[1] == 'P'.code.toByte() &&
                        header[2] == 'D'.code.toByte() &&
                        header[3] == 'F'.code.toByte() &&
                        header[4] == '-'.code.toByte()
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "isPdfHeaderValid check error: ${e.message}")
            false
        }
    }

    suspend fun prepareChunks(context: Context, uri: Uri, fileName: String): List<PdfChunkInfo> = withContext(Dispatchers.IO) {
        if (!isPdfHeaderValid(context, uri)) {
            throw IllegalStateException("The file '$fileName' is empty, corrupted, or not a valid PDF document. Please select a valid PDF file.")
        }

        val fileSize = getFileSize(context, uri)
        val pageCount = getPageCount(context, uri)
        Log.d(TAG, "File $fileName size: $fileSize bytes, pages: $pageCount")

        // If file is under 8MB & <= 10 pages, attempt single chunk without disk splitting
        if (fileSize <= MAX_SINGLE_FILE_SIZE && pageCount <= 10) {
            try {
                val bytes = readBytesFromUri(context, uri)
                if (bytes.isNotEmpty()) {
                    return@withContext listOf(
                        PdfChunkInfo(
                            chunkIndex = 1,
                            totalChunks = 1,
                            startPage = 1,
                            endPage = pageCount,
                            rawBytes = bytes
                        )
                    )
                }
            } catch (oom: OutOfMemoryError) {
                Log.w(TAG, "Single chunk load OOM, switching to disk-backed split mode")
                System.gc()
            } catch (e: Exception) {
                Log.w(TAG, "Single chunk read failed: ${e.message}, switching to disk-backed split mode")
            }
        }

        // File is large or memory constrained -> Split programmatically
        Log.i(TAG, "File $fileName ($fileSize bytes, $pageCount pages) splitting via PDFBox...")
        
        try {
            PDFBoxResourceLoader.init(context)
        } catch (e: Exception) {
            Log.w(TAG, "PDFBox init notice: ${e.message}")
        }

        val tempChunks = mutableListOf<PdfChunkInfo>()
        var tempInputFile: File? = null
        var pdDocument: PDDocument? = null

        try {
            tempInputFile = copyUriToTempFile(context, uri)
            pdDocument = PDDocument.load(tempInputFile)
            val totalPages = pdDocument.numberOfPages.coerceAtLeast(1)
            val pagesPerChunk = calculatePagesPerChunk(fileSize, totalPages)
            val totalChunks = kotlin.math.ceil(totalPages.toDouble() / pagesPerChunk).toInt().coerceAtLeast(1)

            var chunkIdx = 0
            var startPage = 0
            while (startPage < totalPages) {
                val endPage = minOf(startPage + pagesPerChunk - 1, totalPages - 1)
                
                var chunkDoc: PDDocument? = null
                val chunkFile = File(context.cacheDir, "chunk_${System.currentTimeMillis()}_${chunkIdx}.pdf")
                try {
                    chunkDoc = PDDocument()
                    for (i in startPage..endPage) {
                        chunkDoc.addPage(pdDocument.getPage(i))
                    }
                    
                    FileOutputStream(chunkFile).use { fos ->
                        BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
                            chunkDoc.save(bos)
                        }
                    }
                    
                    tempChunks.add(
                        PdfChunkInfo(
                            chunkIndex = chunkIdx + 1,
                            totalChunks = totalChunks,
                            startPage = startPage + 1,
                            endPage = endPage + 1,
                            chunkFile = chunkFile
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error generating sub-chunk $chunkIdx: ${e.message}")
                    chunkFile.delete()
                    throw e
                } finally {
                    try {
                        chunkDoc?.close()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                
                chunkIdx++
                startPage += pagesPerChunk
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError during PDFBox splitting: ${e.message}")
            System.gc()
            // Clean up any partially created chunks
            tempChunks.forEach { it.cleanup() }
            tempChunks.clear()
            throw IllegalStateException("PDF file size exceeds available memory limit. Please try a compressed PDF.")
        } catch (e: Exception) {
            Log.e(TAG, "PDFBox splitting failed: ${e.message}.", e)
            tempChunks.forEach { it.cleanup() }
            tempChunks.clear()
            
            // Safe fallback: try reading raw bytes directly with memory recovery
            try {
                val bytes = readBytesFromUri(context, uri)
                if (bytes.isNotEmpty()) {
                    tempChunks.add(
                        PdfChunkInfo(
                            chunkIndex = 1,
                            totalChunks = 1,
                            startPage = 1,
                            endPage = pageCount,
                            rawBytes = bytes
                        )
                    )
                }
            } catch (oom: OutOfMemoryError) {
                System.gc()
                throw IllegalStateException("Failed to process PDF: File size exceeds device memory limits.")
            }
        } finally {
            try {
                pdDocument?.close()
            } catch (e: Exception) {
                // ignore
            }
            try {
                tempInputFile?.let { if (it.exists()) it.delete() }
            } catch (e: Exception) {
                // ignore
            }
        }

        if (tempChunks.isEmpty()) {
            val fallbackBytes = readBytesFromUri(context, uri)
            return@withContext listOf(
                PdfChunkInfo(
                    chunkIndex = 1,
                    totalChunks = 1,
                    startPage = 1,
                    endPage = pageCount,
                    rawBytes = fallbackBytes
                )
            )
        }

        return@withContext tempChunks
    }

    private fun copyUriToTempFile(context: Context, uri: Uri): File {
        val tempFile = File(context.cacheDir, "temp_split_${System.currentTimeMillis()}_${(1000..9999).random()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedInputStream(input, BUFFER_SIZE).use { bis ->
                FileOutputStream(tempFile).use { fos ->
                    BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
                        bis.copyTo(bos, BUFFER_SIZE)
                    }
                }
            }
        } ?: throw IllegalStateException("Unable to open input stream for selected PDF file.")
        return tempFile
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun getPageCount(context: Context, uri: Uri): Int {
        if (!isPdfHeaderValid(context, uri)) {
            Log.w(TAG, "Cannot get page count: PDF header invalid or corrupted")
            return 1
        }
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                renderer.pageCount
            } else {
                1
            }
        } catch (e: Exception) {
            Log.w(TAG, "getPageCount failed (${e.message}), defaulting to 1")
            1
        } finally {
            try {
                renderer?.close()
            } catch (e: Exception) {
                // ignore
            }
            try {
                pfd?.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun readBytesFromUri(context: Context, uri: Uri): ByteArray {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedInputStream(inputStream, BUFFER_SIZE).use { bis ->
                    bis.readBytes()
                }
            } ?: ByteArray(0)
        } catch (oom: OutOfMemoryError) {
            System.gc()
            throw oom
        } catch (e: Exception) {
            Log.e(TAG, "readBytesFromUri error: ${e.message}")
            ByteArray(0)
        }
    }

    private fun calculatePagesPerChunk(fileSize: Long, totalPages: Int): Int {
        if (totalPages <= 0) return DEFAULT_PAGES_PER_CHUNK
        val avgBytesPerPage = maxOf(1L, fileSize / totalPages)
        val targetMaxChunkSize = 4 * 1024 * 1024L // 4 MB target chunk size for safety
        val calculated = (targetMaxChunkSize / avgBytesPerPage).toInt()
        return calculated.coerceIn(2, 12)
    }
}
