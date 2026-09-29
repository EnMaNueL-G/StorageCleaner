package com.enmanuelgil.storagecleaner.model

/** Reparto del almacenamiento interno. Los desgloses solo existen con el "acceso de uso". */
data class Overview(
    val total: Long = 0,
    val free: Long = 0,
    val detailed: Boolean = false,
    val apps: Long = 0,      // APK + datos de las apps (sin su caché)
    val cache: Long = 0,     // caché de todas las apps
    val images: Long = 0,
    val videos: Long = 0,
    val audio: Long = 0,
    val otherFiles: Long = 0, // documentos, descargas, APK sueltos, etc.
    val otherKnown: Boolean = true, // Android 8-11 no permite separarlo del resto
    val system: Long = 0,     // Android y lo que no se puede atribuir
) {
    val used get() = (total - free).coerceAtLeast(0)
    val usedFraction get() = if (total > 0) used.toFloat() / total else 0f
}

data class AppStorage(
    val pkg: String,
    val label: String,
    val appBytes: Long,
    val dataBytes: Long,   // sin la caché
    val cacheBytes: Long,
    val isSystem: Boolean,
) {
    val total get() = appBytes + dataBytes + cacheBytes
}

enum class FileKind(val title: String, val hint: String) {
    APK("Instaladores APK", "Los APK ya instalados se pueden borrar sin perder la app."),
    DUPLICATE("Archivos duplicados", "Copias exactas (mismo contenido). Se conserva siempre una."),
    LARGE("Archivos grandes", "De 100 MB o más. Revísalos antes de borrar."),
    OLD_DOWNLOAD("Descargas antiguas", "En Descargas y sin tocar desde hace más de 90 días."),
}

data class FileItem(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val kind: FileKind,
    val note: String = "",
    val suggested: Boolean = false, // preseleccionado porque borrarlo es seguro
    val group: Int = 0,             // grupo de duplicados
)

data class ScanResult(
    val items: List<FileItem> = emptyList(),
    val scannedFiles: Int = 0,
    val finishedAt: Long = 0,
) {
    fun of(kind: FileKind) = items.filter { it.kind == kind }
}

fun archivos(n: Int) = if (n == 1) "1 archivo" else "$n archivos"

/** Unidades decimales (1 GB = 1000 MB), las mismas que usa Ajustes > Almacenamiento desde Android 8. */
fun Long.formatSize(): String = when {
    this >= 1_000_000_000L -> "%.1f GB".format(this / 1e9)
    this >= 1_000_000L -> "%.0f MB".format(this / 1e6)
    this >= 1_000L -> "%.0f KB".format(this / 1e3)
    else -> "$this B"
}
