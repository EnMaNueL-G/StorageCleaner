package com.enmanuelgil.storagecleaner.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.storagecleaner.model.*
import com.enmanuelgil.storagecleaner.ui.theme.*
import com.enmanuelgil.storagecleaner.viewmodel.CleanResult

@Composable
fun DashboardScreen(
    storage: StorageInfo,
    isLoading: Boolean,
    isCleaning: Boolean,
    lastResult: CleanResult?,
    onAnalyze: () -> Unit,
    onClearCache: () -> Unit,
    onDismissResult: () -> Unit = {}
) {
    // Usar LazyColumn en lugar de Column+verticalScroll para evitar el bug de Compose SlotTable
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        // Header
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("StorageCleaner", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Análisis de almacenamiento", fontSize = 12.sp, color = TextSecondary)
                }
                IconButton(onClick = onAnalyze, enabled = !isLoading && !isCleaning) {
                    Icon(Icons.Default.Refresh, contentDescription = "Analizar", tint = CleanBlue)
                }
            }
        }

        // Loading
        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator(color = CleanBlue, modifier = Modifier.size(48.dp))
                        Text("Analizando almacenamiento…", fontSize = 14.sp, color = TextSecondary)
                    }
                }
            }
            return@LazyColumn
        }

        // Confirmación de limpieza — siempre visible al completar (auto-cierre 4s)
        lastResult?.let { result ->
            item {
                LaunchedEffect(result) {
                    kotlinx.coroutines.delay(4000)
                    onDismissResult()
                }
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CleanGreen.copy(alpha = 0.13f)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CleanGreen.copy(alpha = 0.3f))
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = CleanGreen, modifier = Modifier.size(32.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "✅ Limpieza completada",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                            )
                            Text(
                                result.message.ifEmpty { "Caché limpiada correctamente." },
                                fontSize = 12.sp, color = CleanGreen, lineHeight = 17.sp
                            )
                        }
                        IconButton(onClick = onDismissResult, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Gráfico circular (solo si hay datos)
        if (storage.totalBytes > 0) {
            item { StorageDonut(storage) }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StorageChip(Modifier.weight(1f), "Usado",  storage.usedBytes.formatSize(), storageColor(storage.usedBytes.toFloat() / storage.totalBytes))
                    StorageChip(Modifier.weight(1f), "Libre",  storage.freeBytes.formatSize(),  CleanGreen)
                    StorageChip(Modifier.weight(1f), "Total",  storage.totalBytes.formatSize(), CleanBlue)
                }
            }
        }

        // Botón limpiar caché
        item { ClearCacheButton(isCleaning, onClearCache, storage) }

        // Categorías
        if (storage.categories.isNotEmpty()) {
            item {
                Text("Desglose por categoría", fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
            items(storage.categories, key = { it.name }) { cat ->
                CategoryCard(cat)
            }
        }
    }
}

@Composable
fun StorageDonut(storage: StorageInfo) {
    val usedPct = if (storage.totalBytes > 0)
        (storage.usedBytes.toFloat() / storage.totalBytes).coerceIn(0f, 1f) else 0f

    // Animación segura: target siempre entre 0 y 1
    val animPct by animateFloatAsState(
        targetValue = usedPct,
        animationSpec = tween(durationMillis = 900, easing = EaseOutCubic),
        label = "storageArc"
    )
    val color = storageColor(usedPct)

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Arco — guardado para evitar crash cuando size = 0
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                Canvas(modifier = Modifier.size(120.dp)) {
                    if (size.width <= 0f || size.height <= 0f) return@Canvas
                    val stroke = 14.dp.toPx()
                    val inset  = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(inset, inset)

                    // Fondo
                    drawArc(color = SurfaceDark, startAngle = 0f, sweepAngle = 360f,
                        useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round),
                        topLeft = topLeft, size = arcSize)
                    // Progreso (mínimo 2° para que sea visible)
                    val sweep = (360f * animPct).coerceAtLeast(if (animPct > 0f) 2f else 0f)
                    if (sweep > 0f) {
                        drawArc(color = color, startAngle = -90f, sweepAngle = sweep,
                            useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round),
                            topLeft = topLeft, size = arcSize)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(usedPct * 100).toInt()}%",
                        fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
                    Text("usado", fontSize = 11.sp, color = TextSecondary)
                }
            }

            // Info a la derecha del arco
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                StorageRow("💾", "Total",  storage.totalBytes.formatSize(), TextPrimary)
                StorageRow("📦", "Usado",  storage.usedBytes.formatSize(),  color)
                StorageRow("✅", "Libre",  storage.freeBytes.formatSize(),  CleanGreen)
            }
        }
    }
}

@Composable
private fun StorageRow(emoji: String, label: String, value: String,
                       color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 13.sp)
            Text(label, fontSize = 13.sp, color = TextSecondary)
        }
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun StorageChip(modifier: Modifier, label: String, value: String,
                color: androidx.compose.ui.graphics.Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 10.sp, color = TextSecondary)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun ClearCacheButton(isCleaning: Boolean, onClear: () -> Unit, storage: StorageInfo) {
    val cacheSize = storage.categories.find { it.icon == CategoryIcon.CACHE }?.sizeBytes ?: 0L
    val tint = if (isCleaning) CleanOrange else CleanTeal

    Card(
        onClick = { if (!isCleaning) onClear() },
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                if (isCleaning) Icons.Default.HourglassTop else Icons.Default.CleaningServices,
                null, tint = tint, modifier = Modifier.size(32.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    if (isCleaning) "Limpiando…" else "Limpiar Caché",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = tint
                )
                Text(
                    if (cacheSize > 0) "Liberar hasta ${cacheSize.formatSize()}"
                    else "Elimina archivos temporales de todas las apps",
                    fontSize = 12.sp, color = TextSecondary
                )
            }
            if (!isCleaning && cacheSize > 0) {
                Surface(shape = RoundedCornerShape(8.dp), color = tint.copy(alpha = 0.2f)) {
                    Text(cacheSize.formatSize(), Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tint)
                }
            }
        }
    }
}

@Composable
fun CategoryCard(category: StorageCategory) {
    // key en remember para evitar estado compartido entre items
    var expanded by remember(category.name) { mutableStateOf(false) }

    val iconColor = when (category.icon) {
        CategoryIcon.CACHE     -> CleanOrange
        CategoryIcon.APPS      -> CleanBlue
        CategoryIcon.DOWNLOADS -> CleanTeal
        else                   -> TextSecondary
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)) {
                    Icon(categoryIcon(category.icon), null, tint = iconColor, modifier = Modifier.size(22.dp))
                    Column {
                        Text(category.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("${category.items.size} elementos", fontSize = 11.sp, color = TextSecondary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = iconColor.copy(alpha = 0.15f)) {
                        Text(category.sizeBytes.formatSize(),
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = iconColor)
                    }
                    if (category.items.isNotEmpty()) {
                        IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null, tint = TextSecondary, modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (expanded && category.items.isNotEmpty()) {
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.08f))
                category.items.take(12).forEach { item ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.name, fontSize = 12.sp, color = TextSecondary,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.sizeBytes.formatSize(), fontSize = 12.sp,
                            color = TextPrimary, fontWeight = FontWeight.Medium)
                    }
                }
                if (category.items.size > 12) {
                    Text("… y ${category.items.size - 12} elementos más",
                        fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun categoryIcon(icon: CategoryIcon) = when (icon) {
    CategoryIcon.CACHE     -> Icons.Default.Memory
    CategoryIcon.APPS      -> Icons.Default.Android
    CategoryIcon.DOWNLOADS -> Icons.Default.Download
    CategoryIcon.MEDIA     -> Icons.Default.Image
    CategoryIcon.SYSTEM    -> Icons.Default.Settings
    CategoryIcon.OTHER     -> Icons.Default.Folder
}
