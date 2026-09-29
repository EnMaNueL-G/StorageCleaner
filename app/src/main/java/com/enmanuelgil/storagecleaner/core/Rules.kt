package com.enmanuelgil.storagecleaner.core

import com.enmanuelgil.storagecleaner.model.Overview

/** Reglas puras (sin Android) para poder probarlas con tests. */
object Rules {
    const val LARGE_BYTES = 100_000_000L // 100 MB, en las mismas unidades que muestra la app
    const val MIN_DUP_BYTES = 1_000_000L
    const val OLD_DOWNLOAD_MS = 90L * 24 * 3600 * 1000

    fun isApk(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".xapk") || n.endsWith(".apkm")
    }

    /** Un APK se puede borrar sin perder nada si su app ya está instalada con esa versión o una más nueva. */
    fun apkRedundant(installedVersion: Long?, apkVersion: Long?): Boolean =
        installedVersion != null && apkVersion != null && installedVersion >= apkVersion

    fun isOldDownload(modified: Long, now: Long): Boolean = modified > 0 && now - modified > OLD_DOWNLOAD_MS

    /** ¿Está [path] dentro de la carpeta [dir] (no en otra que solo empiece igual, como "Download_old")? */
    fun isInside(path: String, dir: String): Boolean = path.startsWith(dir.trimEnd('/') + "/")

    /** Carpetas de fotos, vídeos, descargas y documentos del usuario, donde una copia sobrante no rompe ninguna app. */
    private val USER_MEDIA = listOf("/DCIM/", "/Pictures/", "/Movies/", "/Download/", "/Documents/", "/Music/",
        "/WhatsApp/Media/", "/Telegram/")

    fun isUserMedia(path: String): Boolean = USER_MEDIA.any { path.contains(it, ignoreCase = true) }

    data class Candidate(val path: String, val size: Long, val modified: Long)

    /**
     * Agrupa duplicados exactos: mismo tamaño, misma huella rápida ([quick]: principio y final del archivo)
     * y mismo hash completo ([full]). Solo se lee lo necesario: el hash completo solo de los que ya coinciden
     * en lo demás. Si una función devuelve null (archivo ilegible), ese archivo no entra en ningún grupo.
     */
    fun duplicateGroups(
        files: List<Candidate>,
        quick: (Candidate) -> String?,
        full: (Candidate) -> String?,
    ): List<List<Candidate>> {
        fun split(list: List<Candidate>, key: (Candidate) -> String?): List<List<Candidate>> =
            list.mapNotNull { c -> key(c)?.let { it to c } }
                .groupBy({ it.first }, { it.second }).values.filter { it.size > 1 }
        val out = ArrayList<List<Candidate>>()
        files.filter { it.size >= MIN_DUP_BYTES }.groupBy { it.size }.values
            .filter { it.size > 1 }
            .forEach { sameSize ->
                split(sameSize, quick).forEach { sameQuick ->
                    split(sameQuick, full).forEach { out.add(orderKeeperFirst(it)) }
                }
            }
        return out.sortedByDescending { g -> g.first().size * (g.size - 1) }
    }

    /** El primero es el que se conserva: el de la cámara (DCIM) si lo hay, si no el más antiguo, si no la ruta más corta. */
    fun orderKeeperFirst(group: List<Candidate>): List<Candidate> =
        group.sortedWith(
            compareBy<Candidate>({ if (it.path.contains("/DCIM/", ignoreCase = true)) 0 else 1 })
                .thenBy { if (it.modified > 0) it.modified else Long.MAX_VALUE }
                .thenBy { it.path.length }
                .thenBy { it.path }
        )

    /**
     * ¿Se pueden borrar las copias marcadas de un grupo de duplicados? Sí si el usuario marcó el grupo entero
     * (se le avisó) o si al menos una copia NO marcada sigue en su sitio ([stillThere]). Así nunca se pierde el
     * archivo porque la copia "que se conserva" se haya borrado o movido desde el escaneo.
     */
    fun dupDeletionAllowed(group: List<String>, selected: Set<String>, stillThere: (String) -> Boolean): Boolean {
        val kept = group.filter { it !in selected }
        return kept.isEmpty() || kept.any(stillThere)
    }

    /**
     * Reparto como el de Ajustes > Almacenamiento. [dataBytes] de Android incluye la caché, y la parte de las
     * apps en el almacenamiento compartido ([extAppBytes], solo Android 12+) ya está dentro de los datos.
     * Si [extAppBytes] es null (Android 8-11) no se puede separar "Otros archivos" de lo que las apps guardan
     * ahí, así que todo eso va a "Sistema y otros" en vez de contarlo dos veces.
     */
    fun overview(
        total: Long, free: Long,
        appBytes: Long, dataBytes: Long, cacheBytes: Long,
        extTotal: Long, images: Long, videos: Long, audio: Long, extAppBytes: Long?,
    ): Overview {
        val used = (total - free).coerceAtLeast(0)
        val apps = appBytes + (dataBytes - cacheBytes).coerceAtLeast(0)
        val other = if (extAppBytes == null) 0L else (extTotal - images - videos - audio - extAppBytes).coerceAtLeast(0)
        val known = apps + cacheBytes + images + videos + audio + other
        return Overview(
            total = total, free = free, detailed = true,
            apps = apps, cache = cacheBytes, images = images, videos = videos, audio = audio,
            otherFiles = other, otherKnown = extAppBytes != null, system = (used - known).coerceAtLeast(0),
        )
    }
}
