package com.enmanuelgil.storagecleaner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.storagecleaner.core.Access
import com.enmanuelgil.storagecleaner.core.CacheCleaner
import com.enmanuelgil.storagecleaner.core.FileScanner
import com.enmanuelgil.storagecleaner.core.Rules
import com.enmanuelgil.storagecleaner.core.StorageStats
import com.enmanuelgil.storagecleaner.model.AppStorage
import com.enmanuelgil.storagecleaner.model.FileItem
import com.enmanuelgil.storagecleaner.model.FileKind
import com.enmanuelgil.storagecleaner.model.Overview
import com.enmanuelgil.storagecleaner.model.ScanResult
import com.enmanuelgil.storagecleaner.model.archivos
import com.enmanuelgil.storagecleaner.model.formatSize
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    val overview = MutableStateFlow(Overview())
    val apps = MutableStateFlow<List<AppStorage>>(emptyList())
    val loading = MutableStateFlow(false)
    val hasUsage = MutableStateFlow(false)
    val hasFiles = MutableStateFlow(false)

    val cleaning = MutableStateFlow(false)
    /** Mensaje del último resultado (limpieza o borrado), null si no hay. */
    val message = MutableStateFlow<String?>(null)

    val scan = MutableStateFlow<ScanResult?>(null)
    val scanning = MutableStateFlow(false)
    val scanPhase = MutableStateFlow("")
    val deleting = MutableStateFlow(false)
    val selected = MutableStateFlow<Set<String>>(emptySet())

    private var cacheBefore: Pair<Long, Long?>? = null

    init { refresh() }

    /** Se llama al abrir y al volver a la app (por si el usuario acaba de dar un permiso). */
    fun refresh() {
        val u = Access.usage(ctx); val f = Access.files(ctx)
        val changed = u != hasUsage.value || f != hasFiles.value
        hasUsage.value = u; hasFiles.value = f
        if (!loading.value && (changed || overview.value.total == 0L)) load()
    }

    fun load() {
        viewModelScope.launch {
            loading.value = true
            val (o, a) = withContext(Dispatchers.IO) { StorageStats.apps(ctx).let { StorageStats.overview(ctx, it) to it } }
            overview.value = o; apps.value = a
            loading.value = false
        }
    }

    /** Limpieza que no necesita pantallas del sistema: Android borra la caché que decida (allocateBytes). */
    fun cleanCache() {
        if (cleaning.value) return
        viewModelScope.launch {
            cleaning.value = true
            val r = withContext(Dispatchers.IO) {
                val before = CacheCleaner.measure(ctx)
                CacheCleaner.askSystem(ctx)
                CacheCleaner.result(ctx, before)
            }
            message.value = cacheMessage(r, systemScreen = false)
            cleaning.value = false
            load()
        }
    }

    /**
     * Antes de la pantalla del sistema que limpia caché (Android 11+): se mide la caché fuera del hilo
     * principal (consulta cada app, puede tardar) y después se llama a [launch] para abrir la pantalla.
     */
    fun beforeSystemClear(launch: () -> Unit) {
        if (cleaning.value) return
        cleaning.value = true
        viewModelScope.launch {
            cacheBefore = withContext(Dispatchers.IO) { CacheCleaner.measure(ctx) }
            launch()
        }
    }

    fun cancelSystemClear() { cacheBefore = null; cleaning.value = false }

    fun afterSystemClear() {
        val before = cacheBefore ?: return
        cacheBefore = null
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { CacheCleaner.result(ctx, before) }
            message.value = cacheMessage(r, systemScreen = true)
            cleaning.value = false
            load()
        }
    }

    private fun cacheMessage(r: CacheCleaner.Result, systemScreen: Boolean): String {
        val left = r.cacheAfter?.let { " Queda ${it.formatSize()} de caché." } ?: ""
        return when {
            r.freedBytes >= 1_000_000 -> "Liberados ${r.freedBytes.formatSize()} de caché.$left"
            systemScreen -> "No se liberó caché (quizá cancelaste o ya estaba vacía).$left"
            else -> "Android no ha borrado caché ahora: solo la borra cuando lo considera necesario.$left" +
                "\nPuedes vaciar la de una app concreta desde la pestaña Apps."
        }
    }

    fun dismissMessage() { message.value = null }

    private var scanJob: Job? = null

    fun startScan() {
        if (scanning.value || deleting.value || !Access.files(ctx)) return
        scanJob = viewModelScope.launch {
            scanning.value = true; scanPhase.value = "Revisando…"
            try {
                val r = withContext(Dispatchers.IO) {
                    FileScanner.scan(ctx, progress = { scanPhase.value = it }, check = { ensureActive() })
                }
                scan.value = r
                selected.value = r.items.filter { it.suggested }.map { it.path }.toSet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                message.value = "No se pudo terminar la búsqueda (${e.javaClass.simpleName}). Inténtalo de nuevo."
            } finally {
                scanning.value = false
            }
        }
    }

    fun cancelScan() { scanJob?.cancel() }

    fun toggle(path: String) {
        selected.value = selected.value.let { if (path in it) it - path else it + path }
    }

    fun setSelection(paths: Collection<String>, on: Boolean) {
        selected.value = if (on) selected.value + paths else selected.value - paths.toSet()
    }

    fun deleteSelected() {
        val s0 = scan.value ?: return
        val sel = selected.value
        if (sel.isEmpty() || deleting.value || scanning.value) return
        viewModelScope.launch {
            deleting.value = true
            try {
                val (msg, newScan, gone) = withContext(Dispatchers.IO) { deleteSafely(s0, sel) }
                message.value = msg
                scan.value = newScan
                // Nunca dejar marcada la copia que ahora se conserva.
                val keepers = newScan.items.filter { it.kind == FileKind.DUPLICATE && !it.suggested && it.note == "Se conserva esta" }.map { it.path }.toSet()
                selected.value = selected.value - gone - keepers
            } finally {
                deleting.value = false
            }
            load()
        }
    }

    /** Borra lo marcado sin perder nunca un archivo duplicado del que no quede otra copia. */
    private fun deleteSafely(s: ScanResult, sel: Set<String>): Triple<String, ScanResult, Set<String>> {
        val dupGroups = s.items.filter { it.kind == FileKind.DUPLICATE }.groupBy { it.group }
        val blocked = HashSet<String>()
        var blockedGroups = 0
        for ((_, g) in dupGroups) {
            if (g.none { it.path in sel }) continue
            val ok = Rules.dupDeletionAllowed(g.map { it.path }, sel) { p ->
                val f = File(p); val size = g.first { it.path == p }.size
                f.isFile && f.length() == size
            }
            if (!ok) { blocked += g.map { it.path }.filter { it in sel }; blockedGroups++ }
        }
        val toDelete = sel.filter { it !in blocked }
        val (n, bytes) = FileScanner.delete(ctx, toDelete)
        val failed = toDelete.size - n
        val gone = toDelete.filter { !File(it).exists() }.toSet()

        // Rehacer los grupos de duplicados con lo que queda: nuevo "se conserva" si el anterior ya no está.
        val left = s.items.filter { it.path !in gone }
        val others = left.filter { it.kind != FileKind.DUPLICATE }
        val dups = left.filter { it.kind == FileKind.DUPLICATE }.groupBy { it.group }.values
            .filter { it.size > 1 }
            .flatMap { g ->
                val ordered = Rules.orderKeeperFirst(g.map { Rules.Candidate(it.path, it.size, it.modified) })
                val keeper = ordered.first().path
                val media = g.all { Rules.isUserMedia(it.path) }
                ordered.map { c ->
                    val it0 = g.first { it.path == c.path }
                    if (c.path == keeper) it0.copy(note = "Se conserva esta", suggested = false)
                    else it0.copy(note = "Copia de ${FileScanner.shortPath(keeper)}", suggested = media)
                }
            }
        val items = (others + dups).sortedWith(compareBy<FileItem>({ it.kind.ordinal }, { if (it.kind == FileKind.DUPLICATE) it.group else 0 })
            .thenByDescending { if (it.kind == FileKind.DUPLICATE) 0 else it.size })

        val msg = buildString {
            append(if (n == 0) "No se ha borrado nada." else "Borrado${if (n == 1) "" else "s"} ${archivos(n)} (${bytes.formatSize()}).")
            if (failed > 0) append(" ${archivos(failed)} no se pudieron borrar.")
            if (blockedGroups > 0) append(" No se tocaron $blockedGroups grupo(s) de duplicados porque la copia que se conservaba ya no está: vuelve a buscar.")
        }
        return Triple(msg, s.copy(items = items), gone)
    }
}
