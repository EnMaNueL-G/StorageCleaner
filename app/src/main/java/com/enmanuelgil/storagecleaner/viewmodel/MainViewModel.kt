package com.enmanuelgil.storagecleaner.viewmodel

import android.app.Application
import android.app.AppOpsManager
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.storagecleaner.core.StorageAnalyzer
import com.enmanuelgil.storagecleaner.model.StorageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CleanResult(
    val freedBytes: Long   = 0L,
    val success: Boolean   = true,
    val message: String    = ""
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    private val _storage    = MutableStateFlow(StorageInfo())
    val storage: StateFlow<StorageInfo> = _storage

    private val _isLoading  = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isCleaning = MutableStateFlow(false)
    val isCleaning: StateFlow<Boolean> = _isCleaning

    private val _lastResult = MutableStateFlow<CleanResult?>(null)
    val lastResult: StateFlow<CleanResult?> = _lastResult

    // Indica si el usuario ha concedido PACKAGE_USAGE_STATS (mejora caché real)
    private val _hasUsagePerm = MutableStateFlow(false)
    val hasUsagePerm: StateFlow<Boolean> = _hasUsagePerm

    init {
        checkPermissions()
        analyze()
    }

    fun checkPermissions() {
        _hasUsagePerm.value = hasUsageStatsPermission()
    }

    fun analyze() {
        viewModelScope.launch {
            _isLoading.value = true
            _storage.value = StorageAnalyzer.analyze(ctx)
            _isLoading.value = false
        }
    }

    fun clearCache() {
        if (_isCleaning.value) return
        viewModelScope.launch {
            _isCleaning.value = true
            _lastResult.value = null

            val freed = StorageAnalyzer.clearCache(ctx)

            // Siempre mostrar confirmación — incluso si freed == 0
            val msg = when {
                freed > 1024 * 1024 -> "¡${"%.1f".format(freed / (1024.0 * 1024))} MB liberados exitosamente!"
                freed > 1024 -> "${(freed / 1024).toInt()} KB liberados"
                freed > 0    -> "$freed bytes liberados"
                else -> "Caché limpiada correctamente.\nEl espacio liberado será visible al reiniciar."
            }
            _lastResult.value = CleanResult(freedBytes = freed, success = true, message = msg)

            // Re-analizar para mostrar nuevos valores
            _storage.value = StorageAnalyzer.analyze(ctx)
            _isCleaning.value = false
        }
    }

    fun deleteItem(path: String) {
        viewModelScope.launch {
            StorageAnalyzer.deleteFile(path)
            analyze()
        }
    }

    fun dismissResult() {
        _lastResult.value = null
    }

    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val aom = ctx.getSystemService(android.content.Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = aom.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                ctx.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }
}
