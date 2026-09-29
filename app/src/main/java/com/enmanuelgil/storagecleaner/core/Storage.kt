package com.enmanuelgil.storagecleaner.core

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import com.enmanuelgil.storagecleaner.model.AppStorage
import com.enmanuelgil.storagecleaner.model.FileItem
import com.enmanuelgil.storagecleaner.model.FileKind
import com.enmanuelgil.storagecleaner.model.Overview
import com.enmanuelgil.storagecleaner.model.ScanResult
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Permisos que la app necesita y que concede el usuario en Ajustes. */
object Access {
    fun usage(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Android 11+: "acceso a todos los archivos". Android 8-10: permiso clásico de almacenamiento. */
    fun files(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

/** Cifras reales, del mismo servicio que usa Ajustes > Almacenamiento (StorageStatsManager). */
object StorageStats {
    private fun ssm(c: Context) = c.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager

    /**
     * Reparto del almacenamiento. Las apps se suman app por app ([apps]), como hace Ajustes: la cifra
     * "por usuario" de Android (queryStatsForUser) sale inflada en algunos móviles (en un Vivo, 18,2 GB
     * frente a los 13,6 GB reales de las apps).
     */
    fun overview(c: Context, apps: List<AppStorage>): Overview {
        val s = ssm(c)
        val uuid = StorageManager.UUID_DEFAULT
        val total = runCatching { s.getTotalBytes(uuid) }.getOrDefault(0L)
        val free = runCatching { s.getFreeBytes(uuid) }.getOrDefault(0L)
        if (!Access.usage(c) || apps.isEmpty()) return Overview(total = total, free = free)
        return try {
            val e = s.queryExternalStatsForUser(uuid, Process.myUserHandle())
            val extApp: Long? = if (Build.VERSION.SDK_INT >= 31) e.appBytes else null
            val cache = apps.sumOf { it.cacheBytes }
            Rules.overview(total, free, apps.sumOf { it.appBytes }, apps.sumOf { it.dataBytes } + cache, cache,
                e.totalBytes, e.imageBytes, e.videoBytes, e.audioBytes, extApp)
        } catch (_: Exception) {
            Overview(total = total, free = free)
        }
    }

    /** Tamaño de cada app instalada (APK, datos y caché). La 1.0.0 consultaba siempre el volumen equivocado. */
    fun apps(c: Context): List<AppStorage> {
        if (!Access.usage(c)) return emptyList()
        val s = ssm(c)
        val pm = c.packageManager
        val user = Process.myUserHandle()
        return pm.getInstalledApplications(0).mapNotNull { ai ->
            try {
                val st = s.queryStatsForPackage(ai.storageUuid, ai.packageName, user)
                AppStorage(
                    pkg = ai.packageName,
                    label = pm.getApplicationLabel(ai).toString(),
                    appBytes = st.appBytes,
                    dataBytes = (st.dataBytes - st.cacheBytes).coerceAtLeast(0),
                    cacheBytes = st.cacheBytes,
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
                )
            } catch (_: Exception) { null }
        }.sortedByDescending { it.total }
    }

    /** Caché total de las apps (suma app por app), o null sin acceso de uso. */
    fun cacheBytes(c: Context): Long? = if (!Access.usage(c)) null else apps(c).sumOf { it.cacheBytes }

    fun freeBytes(c: Context): Long = runCatching { ssm(c).getFreeBytes(StorageManager.UUID_DEFAULT) }.getOrDefault(0L)
}

/**
 * Liberar caché sin root. Android no deja a una app borrar la caché interna de otras (la 1.0.0 lo intentaba
 * y fallaba en silencio). Lo que sí existe:
 *  - Android 11+ con "acceso a todos los archivos": la pantalla del sistema ACTION_CLEAR_APP_CACHE (la lanza
 *    la interfaz). Probado en un Vivo con Android 13: vacía la caché que las apps guardan en el almacenamiento
 *    compartido (Android/data/…/cache); la caché interna de cada app no la toca.
 *  - Cualquier versión: pedir a Android el máximo espacio "asignable" (allocateBytes). Para dárselo, el propio
 *    sistema borra caché de las apps; cuánta, lo decide Android (con espacio de sobra, normalmente nada).
 */
object CacheCleaner {
    data class Result(val freedBytes: Long, val cacheBefore: Long?, val cacheAfter: Long?)

    fun measure(c: Context) = StorageStats.freeBytes(c) to StorageStats.cacheBytes(c)

    fun result(c: Context, before: Pair<Long, Long?>): Result {
        val (free0, cache0) = before
        val (free1, cache1) = measure(c)
        // Lo más fiable es cuánto bajó la caché total; si no hay acceso de uso, el espacio libre ganado.
        val freed = if (cache0 != null && cache1 != null) (cache0 - cache1).coerceAtLeast(0)
                    else (free1 - free0).coerceAtLeast(0)
        return Result(freed, cache0, cache1)
    }

    fun askSystem(c: Context) {
        val sm = c.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val uuid = runCatching { sm.getUuidForPath(c.filesDir) }.getOrDefault(StorageManager.UUID_DEFAULT)
        runCatching {
            val bytes = sm.getAllocatableBytes(uuid)
            if (bytes > 0) sm.allocateBytes(uuid, bytes)
        }
        c.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}

/** Busca en el almacenamiento interno compartido: APK, duplicados, archivos grandes y descargas antiguas. */
object FileScanner {

    val root: String get() = Environment.getExternalStorageDirectory().absolutePath

    private class Entry(val file: File, val path: String, val size: Long, val modified: Long)

    /**
     * [progress] recibe el texto de la fase ("Revisando… 1500 archivos", "Comparando 3/40…").
     * [check] se llama a menudo y lanza CancellationException si se canceló el escaneo.
     */
    fun scan(c: Context, progress: (String) -> Unit, check: () -> Unit): ScanResult {
        val rootDir = Environment.getExternalStorageDirectory()
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        val skip = setOf(File(rootDir, "Android/data").absolutePath, File(rootDir, "Android/obb").absolutePath)
        val all = ArrayList<Entry>()
        val visited = HashSet<String>() // por si hay enlaces simbólicos que forman un bucle
        val stack = ArrayDeque<File>().apply { add(rootDir) }
        while (stack.isNotEmpty()) {
            check()
            val dir = stack.removeLast()
            val canon = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (!visited.add(canon)) continue
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (f in children) {
                if (f.name.startsWith(".")) continue // ocultos: miniaturas, papelera de la galería…
                if (f.isDirectory) { if (f.absolutePath !in skip) stack.add(f) }
                else if (f.isFile) {
                    all.add(Entry(f, f.absolutePath, f.length(), f.lastModified()))
                    if (all.size % 500 == 0) progress("Revisando… ${all.size} archivos")
                }
            }
        }

        val now = System.currentTimeMillis()
        val items = ArrayList<FileItem>()
        val taken = HashSet<String>()

        // 1) APK
        val pm = c.packageManager
        val apks = all.filter { Rules.isApk(it.file.name) }
        apks.forEachIndexed { i, e ->
            check(); progress("Revisando instaladores ${i + 1}/${apks.size}…")
            val f = e.file
            val info = if (f.name.lowercase().endsWith(".apk")) runCatching { pm.getPackageArchiveInfo(e.path, 0) }.getOrNull() else null
            val apkVer = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() }
            val installed = info?.packageName?.let { p ->
                runCatching { pm.getPackageInfo(p, 0) }.getOrNull()
                    ?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() }
            }
            val label = info?.applicationInfo?.let { ai ->
                ai.sourceDir = e.path; ai.publicSourceDir = e.path
                runCatching { pm.getApplicationLabel(ai).toString() }.getOrNull()
            }
            val redundant = Rules.apkRedundant(installed, apkVer)
            val note = when {
                info == null && f.name.lowercase().endsWith(".apk") -> "No se puede leer (dañado o incompleto)"
                info == null -> "Paquete de varias partes"
                redundant -> "${label ?: info.packageName}: ya instalada"
                installed != null -> "${label ?: info.packageName}: más nueva que la instalada"
                else -> "${label ?: info.packageName}: no instalada"
            }
            items.add(FileItem(e.path, f.name, e.size, e.modified, FileKind.APK, note, suggested = redundant))
            taken.add(e.path)
        }

        // 2) Duplicados exactos
        val candidates = all.filter { it.path !in taken }.map { Rules.Candidate(it.path, it.size, it.modified) }
        var hashed = 0
        val groups = Rules.duplicateGroups(candidates,
            quick = { check(); quickHash(it.path, it.size) },
            full = { check(); hashed++; progress("Comparando posibles duplicados ($hashed)…"); sha256(it.path) })
        groups.forEachIndexed { gi, g ->
            // Solo se marcan solas las copias de fotos, vídeos, descargas… Si una app guarda el mismo archivo en
            // dos carpetas suyas, borrar uno podría romperla: se muestra, pero sin marcar.
            val media = g.all { Rules.isUserMedia(it.path) }
            g.forEachIndexed { i, cand ->
                val keep = i == 0
                items.add(FileItem(cand.path, File(cand.path).name, cand.size, cand.modified, FileKind.DUPLICATE,
                    if (keep) "Se conserva esta" else "Copia de ${shortPath(g[0].path)}",
                    suggested = !keep && media, group = gi + 1))
                taken.add(cand.path)
            }
        }

        // 3) Grandes  4) Descargas antiguas
        for (e in all) {
            if (e.path in taken) continue
            if (e.size >= Rules.LARGE_BYTES) {
                items.add(FileItem(e.path, e.file.name, e.size, e.modified, FileKind.LARGE, shortPath(e.file.parent ?: "")))
            } else if (Rules.isInside(e.path, downloads) && Rules.isOldDownload(e.modified, now)) {
                items.add(FileItem(e.path, e.file.name, e.size, e.modified, FileKind.OLD_DOWNLOAD, ""))
            }
        }
        return ScanResult(items.sortedWith(compareBy<FileItem>({ it.kind.ordinal }, { if (it.kind == FileKind.DUPLICATE) it.group else 0 })
            .thenByDescending { if (it.kind == FileKind.DUPLICATE) 0 else it.size }), all.size, now)
    }

    fun shortPath(p: String) = p.removePrefix(root).trimStart('/').ifEmpty { "/" }

    /** Huella rápida: primeros y últimos 64 KB. Descarta casi todos los "mismo tamaño, distinto contenido" sin leerlos enteros. */
    private fun quickHash(path: String, size: Long): String? = try {
        val md = MessageDigest.getInstance("SHA-256")
        val chunk = 1 shl 16
        RandomAccessFile(path, "r").use { f ->
            val buf = ByteArray(chunk)
            val n1 = f.read(buf, 0, minOf(chunk.toLong(), size).toInt()); if (n1 > 0) md.update(buf, 0, n1)
            if (size > 2L * chunk) {
                f.seek(size - chunk)
                val n2 = f.read(buf, 0, chunk); if (n2 > 0) md.update(buf, 0, n2)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { null }

    private fun sha256(path: String): String? = try {
        val md = MessageDigest.getInstance("SHA-256")
        File(path).inputStream().buffered(1 shl 16).use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { null }

    /** Borra los archivos y avisa a la galería para que no queden miniaturas fantasma. Devuelve (borrados, bytes). */
    fun delete(c: Context, paths: List<String>): Pair<Int, Long> {
        var n = 0; var bytes = 0L
        val done = ArrayList<String>()
        for (p in paths) {
            val f = File(p)
            val size = f.length()
            if (f.isFile && f.delete()) { n++; bytes += size; done.add(p) }
        }
        if (done.isNotEmpty()) MediaScannerConnection.scanFile(c, done.toTypedArray(), null, null)
        return n to bytes
    }
}
