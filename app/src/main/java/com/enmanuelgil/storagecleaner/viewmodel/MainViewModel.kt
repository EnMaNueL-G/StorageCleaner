package com.enmanuelgil.storagecleaner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.storagecleaner.core.StorageAnalyzer
import com.enmanuelgil.storagecleaner.model.StorageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CleanResult(val freedBytes: Long = 0, val error: String? = null)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    private val _storage   = MutableStateFlow(StorageInfo())
    val storage: StateFlow<StorageInfo> = _storage

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isCleaning = MutableStateFlow(false)
    val isCleaning: StateFlow<Boolean> = _isCleaning

    private val _lastResult = MutableStateFlow<CleanResult?>(null)
    val lastResult: StateFlow<CleanResult?> = _lastResult

    init { analyze() }

    fun analyze() {
        viewModelScope.launch {
            _isLoading.value = true
            _storage.value = StorageAnalyzer.analyze(ctx)
            _isLoading.value = false
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _isCleaning.value = true
            val freed = StorageAnalyzer.clearCache(ctx)
            _lastResult.value = CleanResult(freedBytes = freed)
            _storage.value = StorageAnalyzer.analyze(ctx)  // re-analizar
            _isCleaning.value = false
        }
    }

    fun deleteItem(path: String) {
        viewModelScope.launch {
            StorageAnalyzer.deleteFile(path)
            analyze()
        }
    }
}
