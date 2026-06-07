package com.enmanuelgil.storagecleaner.core

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import com.enmanuelgil.storagecleaner.model.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StorageAnalyzer {

    suspend fun analyze(context: Context): StorageInfo = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free  = stat.availableBytes
        val used  = total - free

        val categories = buildList {
            add(analyzeAppCache(context))
            add(analyzeApks(context))
            add(analyzeDownloads())
        }.filterNotNull()

        StorageInfo(
            totalBytes  = total,
            usedBytes   = used,
            freeBytes   = free,
            categories  = categories
        )
    }

    private fun analyzeAppCache(context: Context): StorageCategory {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var totalCache = 0L
        val items = mutableListOf<StorageItem>()

        for (app in apps) {
            try {
                val cacheDir = File(app.dataDir, "cache")
                if (cacheDir.exists()) {
                    val size = dirSize(cacheDir)
                    if (size > 0) {
                        totalCache += size
                        items.add(StorageItem(
                            path      = cacheDir.absolutePath,
                            name      = pm.getApplicationLabel(app).toString(),
                            sizeBytes = size,
                            type      = ItemType.CACHE
                        ))
                    }
                }
            } catch (_: Exception) {}
        }

        return StorageCategory(
            name       = "Caché de Apps",
            sizeBytes  = totalCache,
            icon       = CategoryIcon.CACHE,
            items      = items.sortedByDescending { it.sizeBytes },
            canClean   = true
        )
    }

    private fun analyzeApks(context: Context): StorageCategory {
        // Buscar APKs en Downloads y carpetas comunes
        val items = mutableListOf<StorageItem>()
        val searchDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media"),
            File(Environment.getExternalStorageDirectory(), "Telegram")
        )

        for (dir in searchDirs) {
            if (!dir.exists()) continue
            dir.walkTopDown().maxDepth(3).forEach { file ->
                if (file.isFile && file.extension.lowercase() == "apk") {
                    items.add(StorageItem(
                        path      = file.absolutePath,
                        name      = file.name,
                        sizeBytes = file.length(),
                        type      = ItemType.APK
                    ))
                }
            }
        }

        return StorageCategory(
            name       = "APKs descargados",
            sizeBytes  = items.sumOf { it.sizeBytes },
            icon       = CategoryIcon.APPS,
            items      = items.sortedByDescending { it.sizeBytes },
            canClean   = true
        )
    }

    private fun analyzeDownloads(): StorageCategory {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val items = mutableListOf<StorageItem>()
        var total = 0L

        if (downloadsDir.exists()) {
            downloadsDir.listFiles()?.forEach { file ->
                val size = if (file.isDirectory) dirSize(file) else file.length()
                total += size
                items.add(StorageItem(
                    path      = file.absolutePath,
                    name      = file.name,
                    sizeBytes = size,
                    type      = if (file.isDirectory) ItemType.FOLDER else ItemType.FILE
                ))
            }
        }

        return StorageCategory(
            name       = "Descargas",
            sizeBytes  = total,
            icon       = CategoryIcon.DOWNLOADS,
            items      = items.sortedByDescending { it.sizeBytes },
            canClean   = false  // El usuario decide qué borrar
        )
    }

    fun clearCache(context: Context): Long {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var freed = 0L

        for (app in apps) {
            try {
                val cacheDir = File(app.dataDir, "cache")
                if (cacheDir.exists()) {
                    freed += dirSize(cacheDir)
                    cacheDir.deleteRecursively()
                    cacheDir.mkdirs()
                }
                val codeCacheDir = File(app.dataDir, "code_cache")
                if (codeCacheDir.exists()) {
                    freed += dirSize(codeCacheDir)
                    codeCacheDir.deleteRecursively()
                }
            } catch (_: Exception) {}
        }
        return freed
    }

    fun deleteFile(path: String): Boolean {
        return try { File(path).deleteRecursively() } catch (_: Exception) { false }
    }

    private fun dirSize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) size += file.length()
        }
        return size
    }
}
