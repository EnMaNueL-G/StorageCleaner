package com.enmanuelgil.storagecleaner.ui.screens

import android.content.Intent
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enmanuelgil.storagecleaner.model.Overview
import com.enmanuelgil.storagecleaner.model.formatSize
import com.enmanuelgil.storagecleaner.ui.theme.*
import com.enmanuelgil.storagecleaner.viewmodel.MainViewModel

private data class Slice(val label: String, val bytes: Long, val color: Color)

private fun slices(o: Overview) = listOfNotNull(
    Slice("Apps y sus datos", o.apps, CleanBlue),
    Slice("Caché de apps", o.cache, CleanOrange),
    Slice("Fotos", o.images, CleanTeal),
    Slice("Vídeos", o.videos, Color(0xFFAB47BC)),
    Slice("Audio", o.audio, Color(0xFFFFCA28)),
    if (o.otherKnown) Slice("Otros archivos", o.otherFiles, CleanGreen) else null,
    Slice("Sistema y otros", o.system, Color(0xFF78909C)),
)

@Composable
fun OverviewScreen(vm: MainViewModel, onOpenApps: () -> Unit) {
    val ctx = LocalContext.current
    val o by vm.overview.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val hasUsage by vm.hasUsage.collectAsStateWithLifecycle()
    val hasFiles by vm.hasFiles.collectAsStateWithLifecycle()
    val cleaning by vm.cleaning.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    // Android 11+ con acceso a todos los archivos: la pantalla del sistema que vacía la caché de TODAS las apps.
    val systemClear = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.afterSystemClear() }
    val canSystemClear = Build.VERSION.SDK_INT >= 30 && hasFiles

    // Al aparecer un resultado, subir para que se vea.
    val listState = rememberLazyListState()
    LaunchedEffect(message) { if (message != null) listState.animateScrollToItem(0) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
    ) {
        item {
            ScreenTitle("StorageCleaner", "Qué ocupa tu almacenamiento, con los datos de Android") {
                IconButton(onClick = vm::load, enabled = !loading) { Icon(Icons.Default.Refresh, "Actualizar", tint = CleanBlue) }
            }
        }
        message?.let { m -> item { MessageCard(m, vm::dismissMessage) } }
        item { UsageRing(o, loading) }
        if (!hasUsage) item {
            PermissionCard(
                "Ver en qué se va el espacio",
                "Concede «Acceso de uso» a StorageCleaner para ver el reparto (apps, caché, fotos, vídeos…) y cuánto ocupa cada app. Es el mismo dato que Ajustes > Almacenamiento. La app no tiene Internet: nada sale del móvil.",
                "Dar acceso de uso"
            ) { openUsageAccess(ctx) }
        } else item { Breakdown(o) }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CleaningServices, null, tint = CleanOrange)
                        Spacer(Modifier.width(10.dp))
                        Text("Caché de apps", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                        if (hasUsage) Text(o.cache.formatSize(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CleanOrange)
                    }
                    Text(
                        "Archivos temporales que las apps vuelven a crear al usarlas. Borrarla libera espacio por un tiempo; no acelera el móvil ni borra tus datos." +
                            if (canSystemClear) "\nEl botón abre la limpieza de Android: vacía la caché que las apps guardan en el almacenamiento compartido. La caché interna de cada app solo se borra desde su ficha (pestaña Apps)."
                            else if (Build.VERSION.SDK_INT >= 30) "\nCon «acceso a todos los archivos» (pestaña Archivos) puedes usar la limpieza de caché de Android." else "",
                        fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp
                    )
                    Button(
                        onClick = {
                            if (canSystemClear) {
                                vm.beforeSystemClear {
                                    try { systemClear.launch(Intent(StorageManager.ACTION_CLEAR_APP_CACHE)) }
                                    catch (_: Exception) { vm.cancelSystemClear(); vm.cleanCache() }
                                }
                            } else vm.cleanCache()
                        },
                        enabled = !cleaning,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CleanOrange)
                    ) {
                        if (cleaning) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                        Text(if (canSystemClear) "Limpiar caché con Android" else "Pedir a Android que libere caché")
                    }
                    if (hasUsage) TextButton(onClick = onOpenApps) { Text("Ver qué apps tienen más caché →", color = CleanBlue, fontSize = 13.sp) }
                }
            }
        }
        item {
            OutlinedButton(onClick = {
                try { ctx.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) } catch (_: Exception) {}
            }, modifier = Modifier.fillMaxWidth()) { Text("Abrir Almacenamiento de Android", color = TextPrimary) }
        }
    }
}

@Composable
private fun UsageRing(o: Overview, loading: Boolean) {
    val color = storageColor(o.usedFraction)
    Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 14.dp.toPx()
                    val s = Size(size.width - sw, size.height - sw)
                    val tl = Offset(sw / 2, sw / 2)
                    drawArc(SurfaceDark, 0f, 360f, false, tl, s, style = Stroke(sw))
                    drawArc(color, -90f, 360f * o.usedFraction, false, tl, s, style = Stroke(sw, cap = StrokeCap.Round))
                }
                if (loading && o.total == 0L) CircularProgressIndicator(color = CleanBlue)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(o.usedFraction * 100).toInt()} %", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("usado", fontSize = 11.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Stat("Usado", o.used.formatSize(), color)
                Stat("Libre", o.free.formatSize(), CleanGreen)
                Stat("Total", o.total.formatSize(), TextPrimary)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun Breakdown(o: Overview) {
    val list = slices(o)
    val used = o.used.coerceAtLeast(1)
    Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("En qué se va el espacio", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(SurfaceDark)) {
                list.filter { it.bytes > 0 }.forEach { s ->
                    Box(Modifier.fillMaxHeight().weight((s.bytes.toFloat() / used).coerceAtLeast(0.004f)).background(s.color))
                }
            }
            list.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                    Spacer(Modifier.width(10.dp))
                    Text(s.label, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text(s.bytes.formatSize(), fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
            Text(if (o.otherKnown) "«Otros archivos» son documentos, descargas, APK y lo que no es foto, vídeo ni audio. «Sistema» es Android y lo que no se puede atribuir a nada."
                else "«Sistema y otros» incluye Android, documentos, descargas y lo que no es foto, vídeo ni audio (esta versión de Android no permite separarlo).",
                fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)
        }
    }
}
