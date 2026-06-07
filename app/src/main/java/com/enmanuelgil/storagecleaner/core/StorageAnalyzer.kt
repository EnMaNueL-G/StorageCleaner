package com.enmanuelgil.storagecleaner.core

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import com.enmanuelgil.storagecleaner.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object StorageAnalyzer {

    // ── Análisis principal ────────────────────────────────────────────────────
    suspend fun analyze(context: Context): StorageInfo = withContext(Dispatchers.IO) {
        val stat  = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free  = stat.availableBytes
        val used  = (total - free).coerceAtLeast(0L)

        val categories = buildList {
            add(analyzeAppCache(context))
            add(analyzeApks())
            add(analyzeDownloads())
        }.filterNotNull()

        StorageInfo(
            totalBytes = total,
            usedBytes  = used,
            freeBytes  = free,
            categories = categories
        )
    }

    // ── Caché de apps via StorageStatsManager (API 26+) ───────────────────────
    // Usa el mismo permiso PACKAGE_USAGE_STATS que ya tenemos en el manifest.
    // Es la única forma oficial de leer caché de OTRAS apps sin root en Android 8+.
    private fun analyzeAppCache(context: Context): StorageCategory {
        val ssm = try {
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        } catch (_: Exception) { null }

        val sm = try {
            context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        } catch (_: Exception) { null }

        val pm        = context.packageManager
        val myUser    = Process.myUserHandle()
        var totalCache = 0L
        val items     = mutableListOf<StorageItem>()

        val apps = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Exception) { emptyList() }

        for (app in apps) {
            try {
                if (ssm == null) break

                // Obtener UUID del volumen donde está instalada la app
                val storageUuid = try {
                    sm?.getUuidForPath(File(app.dataDir))?.let {
                        // Convertir java.util.UUID → android.os.storage UUID
                        if (it.toString() == StorageManager.UUID_DEFAULT.toString()) StorageManager.UUID_DEFAULT
                        else StorageManager.UUID_DEFAULT
                    } ?: StorageManager.UUID_DEFAULT
                } catch (_: Exception) { StorageManager.UUID_DEFAULT }

                val stats      = ssm.queryStatsForPackage(storageUuid, app.packageName, myUser)
                val cacheBytes = stats.cacheBytes

                if (cacheBytes > 0) {
                    totalCache += cacheBytes
                    val appName = try {
                        pm.getApplicationLabel(app).toString()
                    } catch (_: Exception) { app.packageName }

                    items.add(StorageItem(
                        path      = app.dataDir,
                        name      = appName,
                        sizeBytes = cacheBytes,
                        type      = ItemType.CACHE
                    ))
                }
            } catch (_: SecurityException) {
                // PACKAGE_USAGE_STATS no concedido — intentar método alternativo
                tryLegacyCacheDir(app, pm, items)?.let { totalCache += it }
            } catch (_: Exception) {}
        }

        // Si StorageStatsManager no devolvió nada, intentar método legacy
        if (totalCache == 0L && ssm == null) {
            apps.forEach { app ->
                tryLegacyCacheDir(app, pm, items)?.let { totalCache += it }
            }
        }

        return StorageCategory(
            name      = "Caché de Apps",
            sizeBytes = totalCache,
            icon      = CategoryIcon.CACHE,
            items     = items.sortedByDescending { it.sizeBytes },
            canClean  = true
        )
    }

    // Método alternativo: leer /cache de apps accesibles (funciona para la propia app)
    private fun tryLegacyCacheDir(
        app: ApplicationInfo,
        pm: PackageManager,
        items: MutableList<StorageItem>
    ): Long? {
        return try {
            val cacheDir = File(app.dataDir, "cache")
            if (cacheDir.exists() && cacheDir.canRead()) {
                val size = dirSize(cacheDir)
                if (size > 0) {
                    val appName = try { pm.getApplicationLabel(app).toString() }
                                  catch (_: Exception) { app.packageName }
                    items.add(StorageItem(
                        path      = cacheDir.absolutePath,
                        name      = appName,
                        sizeBytes = size,
                        type      = ItemType.CACHE
                    ))
                    size
                } else null
            } else null
        } catch (_: Exception) { null }
    }

    // ── APKs descargados ───────────────────────────────────────────────────────
    private fun analyzeApks(): StorageCategory {
        val items      = mutableListOf<StorageItem>()
        val searchDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media"),
            File(Environment.getExternalStorageDirectory(), "Telegram")
        )

        for (dir in searchDirs) {
            if (!dir.exists()) continue
            try {
                dir.walkTopDown()
                    .maxDepth(3)
                    .onEnter { subDir -> try { subDir.canRead() } catch (_: Exception) { false } }
                    .forEach { file ->
                        try {
                            if (file.isFile && file.extension.lowercase() == "apk") {
                                items.add(StorageItem(
                                    path      = file.absolutePath,
                                    name      = file.name,
                                    sizeBytes = file.length(),
                                    type      = ItemType.APK
                                ))
                            }
                        } catch (_: Exception) {}
                    }
            } catch (_: SecurityException) {}
              catch (_: Exception) {}
        }

        return StorageCategory(
            name      = "APKs descargados",
            sizeBytes = items.sumOf { it.sizeBytes },
            icon      = CategoryIcon.APPS,
            items     = items.sortedByDescending { it.sizeBytes },
            canClean  = true
        )
    }

    // ── Carpeta Descargas ───────────────────────────────────────────────────────
    private fun analyzeDownloads(): StorageCategory {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val items        = mutableListOf<StorageItem>()
        var total        = 0L

        if (downloadsDir.exists()) {
            try {
                downloadsDir.listFiles()?.forEach { file ->
                    try {
                        val size = if (file.isDirectory) dirSize(file) else file.length()
                        total += size
                        items.add(StorageItem(
                            path      = file.absolutePath,
                            name      = file.name,
                            sizeBytes = size,
                            type      = if (file.isDirectory) ItemType.FOLDER else ItemType.FILE
                        ))
                    } catch (_: Exception) {}
                }
            } catch (_: SecurityException) {}
              catch (_: Exception) {}
        }

        return StorageCategory(
            name      = "Descargas",
            sizeBytes = total,
            icon      = CategoryIcon.DOWNLOADS,
            items     = items.sortedByDescending { it.sizeBytes },
            canClean  = false
        )
    }

    // ── Limpiar caché ─────────────────────────────────────────────────────────
    // Usa StorageManager.allocateBytes() para pedir al sistema que libere caché
    // de todas las apps — la forma correcta en Android moderno.
    fun clearCache(context: Context): Long {
        val sm   = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeBefore = stat.availableBytes

        // 1. Pedir al sistema que libere caché (Android 8+)
        try {
            if (sm != null) {
                // Solicitar 256 MB — el sistema decide cuánto realmente liberar
                sm.allocateBytes(StorageManager.UUID_DEFAULT, 256L * 1024 * 1024)
            }
        } catch (_: Exception) {}

        // 2. Limpiar directorios accesibles directamente
        try {
            val pm   = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                try {
                    val cacheDir = File(app.dataDir, "cache")
                    if (cacheDir.exists() && cacheDir.canRead() && cacheDir.canWrite()) {
                        cacheDir.deleteRecursively()
                        cacheDir.mkdirs()
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 3. Limpiar caché propia de la app
        try { context.cacheDir.deleteRecursively(); context.cacheDir.mkdirs() } catch (_: Exception) {}
        try { context.externalCacheDir?.deleteRecursively() } catch (_: Exception) {}

        // Calcular bytes liberados comparando antes/después
        return try {
            val statAfter  = StatFs(Environment.getDataDirectory().path)
            val freeAfter  = statAfter.availableBytes
            (freeAfter - freeBefore).coerceAtLeast(0L)
        } catch (_: Exception) { 0L }
    }

    fun deleteFile(path: String): Boolean {
        return try { File(path).deleteRecursively() } catch (_: Exception) { false }
    }

    // ── Utilidades ────────────────────────────────────────────────────────────
    private fun dirSize(dir: File): Long {
        var size = 0L
        try {
            dir.walkTopDown()
                .onEnter { subDir -> try { subDir.canRead() } catch (_: Exception) { false } }
                .forEach { file ->
                    try { if (file.isFile) size += file.length() } catch (_: Exception) {}
                }
        } catch (_: Exception) {}
        return size
    }
}
