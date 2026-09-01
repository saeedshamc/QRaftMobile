package com.example.domain.engine

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Robust file export & caching manager for QRaft Studio.
 * - Handles saving generated bitmaps (PNG, JPEG, WEBP) to an isolated temporary cache directory.
 * - Automatically performs cache pruning & cleanup of stale exports.
 * - Generates secure Content URIs via Android FileProvider to prevent FileUriExposedException on all Android versions.
 * - Configures ACTION_SEND sharing intents with correct URI permission grants and ClipData.
 */
object QRFileExportManager {

    private const val EXPORT_DIR_NAME = "qr_exports"
    private const val DEFAULT_MAX_AGE_MS = 60 * 60 * 1000L // 1 hour
    private const val MAX_CACHED_FILES = 50

    /**
     * Gets the isolated cache directory for all exported QR files, creating it if needed.
     */
    fun getExportCacheDir(context: Context): File {
        val dir = File(context.cacheDir, EXPORT_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Cleans up stale export files from the cache directory.
     * Deletes files older than [maxAgeMs] and ensures total file count does not exceed [maxFiles].
     */
    fun cleanOldExportFiles(
        context: Context,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
        maxFiles: Int = MAX_CACHED_FILES
    ): Int {
        var deletedCount = 0
        try {
            val dir = getExportCacheDir(context)
            val files = dir.listFiles() ?: return 0
            val now = System.currentTimeMillis()

            // 1. Delete files older than maxAgeMs
            for (file in files) {
                if (file.isFile && (now - file.lastModified() > maxAgeMs)) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }

            // 2. If still exceeds maxFiles, delete oldest first
            val remainingFiles = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: emptyList()
            if (remainingFiles.size > maxFiles) {
                val excess = remainingFiles.size - maxFiles
                for (i in 0 until excess) {
                    if (remainingFiles[i].delete()) {
                        deletedCount++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return deletedCount
    }

    /**
     * Saves a generated Bitmap to the temporary cache directory with cleanup.
     *
     * @param context Application context
     * @param bitmap The QR code or image bitmap to persist
     * @param fileName Base or full file name
     * @param format Compression format (PNG, JPEG, etc.)
     * @param quality Quality level (0-100)
     * @return The persisted [File], or null if writing failed
     */
    fun saveBitmapToCache(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100
    ): File? {
        // Run lightweight cleanup before writing
        cleanOldExportFiles(context)

        return try {
            val exportDir = getExportCacheDir(context)
            val targetFile = File(exportDir, fileName)

            // Overwrite cleanly if existing
            if (targetFile.exists()) {
                targetFile.delete()
            }

            BufferedOutputStream(FileOutputStream(targetFile), 32768).use { bos ->
                val success = bitmap.compress(format, quality.coerceIn(1, 100), bos)
                bos.flush()
                if (!success) {
                    targetFile.delete()
                    return null
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                targetFile
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves text data (SVG vector, JSON backup, CSV data) to the cache directory.
     */
    fun saveTextToCache(
        context: Context,
        text: String,
        fileName: String
    ): File? {
        cleanOldExportFiles(context)

        return try {
            val exportDir = getExportCacheDir(context)
            val targetFile = File(exportDir, fileName)

            if (targetFile.exists()) {
                targetFile.delete()
            }

            OutputStreamWriter(FileOutputStream(targetFile), StandardCharsets.UTF_8).use { writer ->
                writer.write(text)
                writer.flush()
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                targetFile
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Converts a cache file to a secure Content URI using Android FileProvider.
     * Throws [IllegalArgumentException] if FileProvider authority is invalid.
     */
    fun getContentUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Prepares a share Intent with full Content URI permission grants.
     * Prevents FileUriExposedException and handles Android 7.0+ through 14+ permission models.
     */
    fun createShareIntent(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String,
        extraText: String? = null,
        extraSubject: String? = null
    ): Intent {
        val contentUri = getContentUri(context, file)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            if (!extraText.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, extraText)
            }
            if (!extraSubject.isNullOrBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, extraSubject)
            }
            // ClipData ensures URI read permission is granted across task boundaries on API 29+
            clipData = ClipData.newRawUri(chooserTitle, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Directly launches a secure sharing chooser for the specified file.
     */
    fun shareFileSafely(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String,
        extraText: String? = null,
        extraSubject: String? = null
    ) {
        try {
            if (!file.exists() || file.length() == 0L) {
                Toast.makeText(context, "Export error: File is empty or not found", Toast.LENGTH_SHORT).show()
                return
            }

            val chooserIntent = createShareIntent(
                context = context,
                file = file,
                mimeType = mimeType,
                chooserTitle = chooserTitle,
                extraText = extraText,
                extraSubject = extraSubject
            )
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Sharing failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
        }
    }
}
