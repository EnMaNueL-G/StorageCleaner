package com.enmanuelgil.storagecleaner.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enmanuelgil.storagecleaner.model.AppStorage
import com.enmanuelgil.storagecleaner.model.formatSize
import com.enmanuelgil.storagecleaner.ui.theme.*
import com.enmanuelgil.storagecleaner.viewmodel.MainViewModel

@Composable
fun AppsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val all by vm.apps.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val hasUsage by vm.hasUsage.collectAsStateWithLifecycle()
    var byCache by rememberSaveable { mutableStateOf(false) }
    var showSystem by rememberSaveable { mutableStateOf(false) }

    val apps = remember(all, byCache, showSystem) {
        all.filter { showSystem || !it.isSystem }
            .sortedByDescending { if (byCache) it.cacheBytes else it.total }
            .let { l -> if (byCache) l.filter { it.cacheBytes > 0 } else l }
    }
    val max = apps.firstOrNull()?.let { if (byCache) it.cacheBytes else it.total }?.coerceAtLeast(1) ?: 1L

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenTitle("Apps", "Cuánto ocupa cada app: instalación, datos y caché") {
                if (hasUsage) IconButton(onClick = vm::load, enabled = !loading) { Icon(Icons.Default.Refresh, "Actualizar", tint = CleanBlue) }
            }
        }
        if (!hasUsage) {
            item {
                PermissionCard("Falta un permiso",
                    "Activa «Permitir acceso de uso» para StorageCleaner. Android lo exige para ver el tamaño de las demás apps.",
                    "Dar acceso de uso") { openUsageAccess(ctx) }
            }
            return@LazyColumn
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !byCache, onClick = { byCache = false }, label = { Text("Por tamaño", fontSize = 12.sp) })
                FilterChip(selected = byCache, onClick = { byCache = true }, label = { Text("Por caché", fontSize = 12.sp) })
                Spacer(Modifier.weight(1f))
                Text("Sistema", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.width(6.dp))
                Switch(checked = showSystem, onCheckedChange = { showSystem = it })
            }
        }
        item {
            val sum = apps.sumOf { if (byCache) it.cacheBytes else it.total }
            Text("${apps.size} apps · ${sum.formatSize()}" + if (byCache) " de caché" else " en total", fontSize = 12.sp, color = TextSecondary)
            Text("Toca una app → Almacenamiento → «Borrar caché». Android no deja que otra app lo haga por ti.",
                fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
        }
        if (loading && all.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CleanBlue) } }
        items(apps, key = { it.pkg }) { a -> AppRow(a, byCache, max) { openAppDetails(ctx, a.pkg) } }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
private fun AppRow(a: AppStorage, byCache: Boolean, max: Long, onClick: () -> Unit) {
    val value = if (byCache) a.cacheBytes else a.total
    val frac = (value.toFloat() / max).coerceIn(0.01f, 1f)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("App ${a.appBytes.formatSize()} · Datos ${a.dataBytes.formatSize()} · Caché ${a.cacheBytes.formatSize()}" + if (a.isSystem) " · sistema" else "",
                        fontSize = 11.sp, color = TextSecondary)
                }
                Text(value.formatSize(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (byCache) CleanOrange else CleanBlue)
            }
            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = if (byCache) CleanOrange else CleanBlue, trackColor = SurfaceDark)
        }
    }
}
