package com.example.domain.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Robust, memory-safe cleanup worker that triggers after animation export finishes,
 * fails, or gets cancelled.
 *
 * Scans internal cache, code cache, and temp directory to purge all temporary
 * frame fragments, partial exports, and unneeded bitmap caches.
 */
object AnimationCleanupWorker {

    private const val TAG = "AnimationCleanupWorker"

    // Recognized temporary file prefixes and suffixes used during animated QR & GIF generation
    private val TEMP_PREFIXES = listOf(
        "qraft_animated_",
        "qraft_temp_",
        "qraft_frame_",
        "frame_",
        "anim_frame_",
        "temp_gif_",
        "gif_frame_",
        "qraft_batch_",
        "qraft_preview_",
        "export_temp_"
    )

    private val TEMP_SUFFIXES = listOf(
        ".tmp",
        ".partial",
        ".frag",
        ".bak"
    )

    data class CleanupReport(
        val filesDeleted: Int,
        val bytesFreed: Long,
        val errorCount: Int
    )

    /**
     * Executes internal cache scanning and deletes all temporary frame fragments asynchronously.
     *
     * @param context Application context
     * @param olderThanMs Optional age threshold in milliseconds (0L = purge all matching temp files)
     * @param preserveFile Optional active export file to exclude from deletion
     */
    suspend fun cleanupFrameFragments(
        context: Context,
        olderThanMs: Long = 0L,
        preserveFile: File? = null
    ): CleanupReport = withContext(Dispatchers.IO) {
        var deletedCount = 0
        var freedBytes = 0L
        var errorCount = 0

        val directoriesToScan = mutableListOf<File>()
        context.cacheDir?.let { directoriesToScan.add(it) }
        context.codeCacheDir?.let { directoriesToScan.add(it) }
        context.externalCacheDir?.let { directoriesToScan.add(it) }

        val now = System.currentTimeMillis()

        for (dir in directoriesToScan) {
            if (!dir.exists() || !dir.isDirectory) continue

            try {
                val files = dir.listFiles() ?: continue
                for (file in files) {
                    if (file == preserveFile) continue

                    val name = file.name.lowercase()
                    val matchesPrefix = TEMP_PREFIXES.any { name.startsWith(it.lowercase()) }
                    val matchesSuffix = TEMP_SUFFIXES.any { name.endsWith(it.lowercase()) }

                    // Also check for individual extracted frame pngs (e.g. frame_001_of_010.png)
                    val isFrameFragment = (name.startsWith("frame_") || name.startsWith("anim_")) && name.endsWith(".png")

                    if (matchesPrefix || matchesSuffix || isFrameFragment) {
                        val isStale = (olderThanMs <= 0L) || (now - file.lastModified() > olderThanMs)
                        if (isStale) {
                            val size = file.length()
                            if (file.delete()) {
                                deletedCount++
                                freedBytes += size
                            } else {
                                errorCount++
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning directory: ${dir.absolutePath}", e)
                errorCount++
            }
        }

        Log.d(TAG, "Cleanup completed: deleted $deletedCount temp fragments ($freedBytes bytes freed, $errorCount errors)")
        CleanupReport(deletedCount, freedBytes, errorCount)
    }

    /**
     * Synchronous / fire-and-forget helper for immediate post-export cleanup
     */
    fun triggerSyncCleanup(context: Context, olderThanMs: Long = 0L, preserveFile: File? = null): Int {
        var deleted = 0
        try {
            val cacheDir = context.cacheDir ?: return 0
            val now = System.currentTimeMillis()
            val files = cacheDir.listFiles() ?: return 0
            for (file in files) {
                if (file == preserveFile) continue
                val name = file.name.lowercase()
                val isTemp = TEMP_PREFIXES.any { name.startsWith(it.lowercase()) } ||
                        TEMP_SUFFIXES.any { name.endsWith(it.lowercase()) } ||
                        ((name.startsWith("frame_") || name.startsWith("anim_")) && name.endsWith(".png"))

                if (isTemp && (olderThanMs <= 0L || (now - file.lastModified() > olderThanMs))) {
                    if (file.delete()) {
                        deleted++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync cleanup error", e)
        }
        return deleted
    }
}
