package com.enmanuelgil.storagecleaner.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enmanuelgil.storagecleaner.model.FileItem
import com.enmanuelgil.storagecleaner.model.FileKind
import com.enmanuelgil.storagecleaner.model.archivos
import com.enmanuelgil.storagecleaner.model.formatSize
import com.enmanuelgil.storagecleaner.ui.theme.*
import com.enmanuelgil.storagecleaner.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FilesScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val hasFiles by vm.hasFiles.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val phase by vm.scanPhase.collectAsStateWithLifecycle()
    val deleting by vm.deleting.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val activity = ctx as? android.app.Activity
    val legacyPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        vm.refresh()
        // Denegado con «No volver a preguntar»: Android ya no muestra el diálogo, hay que ir a la ficha de la app.
        if (res.values.any { !it } && activity != null &&
            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            openAppDetails(ctx, ctx.packageName)
    }

    val items = scan?.items.orEmpty()
    val selItems = items.filter { it.path in selected }
    val selBytes = selItems.sumOf { it.size }

    // Al aparecer un resultado, subir para que se vea.
    val listState = rememberLazyListState()
    LaunchedEffect(message) { if (message != null) listState.animateScrollToItem(0) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ScreenTitle("Archivos", "APK sobrantes, duplicados, archivos grandes y descargas antiguas") }
            message?.let { m -> item { MessageCard(m, vm::dismissMessage) } }
            if (!hasFiles) {
                item {
                    if (Build.VERSION.SDK_INT >= 30) PermissionCard(
                        "Permiso para revisar tus archivos",
                        "Activa «Permitir acceso para gestionar todos los archivos». Hace falta para buscar APK, duplicados y archivos grandes en el almacenamiento interno, y para usar la limpieza de caché de Android. Nada se borra sin que lo elijas y lo confirmes.",
                        "Dar acceso a los archivos") { openAllFilesAccess(ctx) }
                    else PermissionCard(
                        "Permiso para revisar tus archivos",
                        "StorageCleaner necesita el permiso de almacenamiento para buscar APK, duplicados y archivos grandes. Nada se borra sin que lo elijas y lo confirmes.",
                        "Dar permiso") {
                        legacyPerm.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    }
                }
                return@LazyColumn
            }
            item {
                Button(onClick = vm::startScan, enabled = !scanning, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CleanBlue)) {
                    if (scanning) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text(phase, maxLines = 1)
                    } else {
                        Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp))
                        Text(if (scan == null) "Buscar archivos que sobran" else "Volver a buscar")
                    }
                }
                if (scanning) TextButton(onClick = vm::cancelScan, modifier = Modifier.fillMaxWidth()) { Text("Cancelar búsqueda", color = TextSecondary) }
            }
            val s = scan
            if (s != null && !scanning) {
                item {
                    Text("${archivos(s.scannedFiles)} revisados · ${items.size} encontrados. Los que es seguro borrar vienen ya marcados.",
                        fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
                }
                if (items.isEmpty()) item { Text("No hay nada que sobre. 👍", fontSize = 14.sp, color = TextPrimary, modifier = Modifier.padding(8.dp)) }
                FileKind.entries.forEach { kind ->
                    val list = s.of(kind)
                    if (list.isEmpty()) return@forEach
                    item(key = "h_$kind") {
                        val anySel = list.any { it.path in selected }
                        Column(Modifier.padding(top = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(kind.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                                Text("${list.size} · ${list.sumOf { it.size }.formatSize()}", fontSize = 12.sp, color = TextSecondary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(kind.hint, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    // En duplicados "marcar todo" nunca marca la copia que se conserva.
                                    val target = if (kind == FileKind.DUPLICATE) list.filter { it.suggested } else list
                                    if (anySel) vm.setSelection(list.map { it.path }, false)
                                    else vm.setSelection(target.map { it.path }, true)
                                }) { Text(if (anySel) "Ninguno" else "Marcar", color = CleanBlue, fontSize = 12.sp) }
                            }
                        }
                    }
                    items(list, key = { "${kind}_${it.path}" }) { f -> FileRow(f, f.path in selected) { vm.toggle(f.path) } }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
        if (selItems.isNotEmpty() && !scanning && !deleting) {
            Surface(color = SurfaceDark) {
                Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().padding(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CleanRed)) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp))
                    Text("Borrar ${selItems.size} (${selBytes.formatSize()})")
                }
            }
        }
    }

    if (confirm) {
        // ¿Hay algún grupo de duplicados con TODAS sus copias marcadas? Entonces se perdería el archivo.
        val lostGroups = items.filter { it.kind == FileKind.DUPLICATE }.groupBy { it.group }.values
            .count { g -> g.all { it.path in selected } }
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = CardDark,
            title = { Text("¿Borrar ${archivos(selItems.size)}?", color = TextPrimary) },
            text = {
                Text("Se liberarán ${selBytes.formatSize()}. Se borran para siempre: no van a ninguna papelera." +
                    if (lostGroups > 0) "\n\n⚠️ En $lostGroups grupo(s) de duplicados has marcado TODAS las copias: ese archivo desaparecerá del todo." else "",
                    color = TextSecondary, lineHeight = 18.sp)
            },
            confirmButton = { TextButton(onClick = { confirm = false; vm.deleteSelected() }) { Text("Borrar", color = CleanRed) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar", color = TextSecondary) } },
        )
    }
}

private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale("es"))

@Composable
private fun FileRow(f: FileItem, checked: Boolean, onToggle: () -> Unit) {
    val keeper = f.kind == FileKind.DUPLICATE && f.note == "Se conserva esta"
    Card(Modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = if (checked) CleanRed.copy(alpha = 0.10f) else CardDark),
        shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(f.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildList {
                    if (f.kind == FileKind.DUPLICATE) add("Grupo ${f.group}")
                    if (f.note.isNotEmpty()) add(f.note)
                    if (f.modified > 0) add(dateFmt.format(Date(f.modified)))
                }.joinToString(" · ")
                Text(sub, fontSize = 11.sp, color = if (keeper) CleanGreen else TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(f.size.formatSize(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}
