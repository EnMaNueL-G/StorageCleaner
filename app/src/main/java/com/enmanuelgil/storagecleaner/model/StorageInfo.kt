package com.enmanuelgil.storagecleaner.model

data class StorageInfo(
    val totalBytes: Long = 0,
    val usedBytes: Long = 0,
    val freeBytes: Long = 0,
    val categories: List<StorageCategory> = emptyList()
)

data class StorageCategory(
    val name: String,
    val sizeBytes: Long,
    val icon: CategoryIcon,
    val items: List<StorageItem> = emptyList(),
    val canClean: Boolean = false
)

data class StorageItem(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val type: ItemType
)

enum class CategoryIcon { APPS, CACHE, DOWNLOADS, MEDIA, SYSTEM, OTHER }
enum class ItemType     { APK, CACHE, FILE, FOLDER }

val Long.mb: Float get() = this / 1024f / 1024f
val Long.gb: Float get() = this / 1024f / 1024f / 1024f

fun Long.formatSize(): String = when {
    this >= 1_073_741_824L -> "%.1f GB".format(this.gb)
    this >= 1_048_576L     -> "%.0f MB".format(this.mb)
    this >= 1_024L         -> "%.0f KB".format(this / 1024f)
    else                   -> "$this B"
}
