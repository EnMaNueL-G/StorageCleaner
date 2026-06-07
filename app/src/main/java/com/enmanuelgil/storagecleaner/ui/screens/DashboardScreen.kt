package com.enmanuelgil.storagecleaner.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    onClearCache: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("StorageCleaner", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            IconButton(onClick = onAnalyze, enabled = !isLoading) {
                Icon(Icons.Default.Refresh, contentDescription = "Analizar", tint = CleanBlue)
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = CleanBlue)
                    Text("Analizando almacenamiento…", fontSize = 14.sp, color = TextSecondary)
                }
            }
            return@Column
        }

        // Resultado de limpieza
        lastResult?.let { result ->
            if (result.freedBytes > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CleanGreen.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CleanGreen, modifier = Modifier.size(28.dp))
                        Column {
                            Text("Limpieza completada", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("${result.freedBytes.formatSize()} liberados", fontSize = 13.sp, color = CleanGreen)
                        }
                    }
                }
            }
        }

        // Gráfico de uso de almacenamiento
        if (storage.totalBytes > 0) {
            StorageDonut(storage)

            // Tarjetas de resumen
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StorageMetricCard(Modifier.weight(1f), "Usado", storage.usedBytes.formatSize(), storageColor(storage.usedBytes.toFloat() / storage.totalBytes))
                StorageMetricCard(Modifier.weight(1f), "Libre", storage.freeBytes.formatSize(), CleanGreen)
                StorageMetricCard(Modifier.weight(1f), "Total", storage.totalBytes.formatSize(), CleanBlue)
            }
        }

        // Botón limpiar caché
        ClearCacheButton(isCleaning, onClearCache, storage)

        // Categorías detectadas
        if (storage.categories.isNotEmpty()) {
            Text("Desglose por categoría", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            storage.categories.forEach { cat ->
                CategoryCard(cat, onDeleteItem = {})
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun StorageDonut(storage: StorageInfo) {
    val usedPct = if (storage.totalBytes > 0) storage.usedBytes.toFloat() / storage.totalBytes else 0f
    val animPct by animateFloatAsState(targetValue = usedPct, animationSpec = tween(900), label = "storage")
    val color = storageColor(usedPct)

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                Canvas(Modifier.size(160.dp)) {
                    val stroke = 18.dp.toPx()
                    val inset  = stroke / 2
                    drawArc(color = CardDark, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                        topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke))
                    drawArc(color = color, startAngle = -90f, sweepAngle = 360f * animPct, useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                        topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(usedPct * 100).toInt()}%", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = color)
                    Text("usado", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun StorageMetricCard(modifier: Modifier, label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun ClearCacheButton(isCleaning: Boolean, onClear: () -> Unit, storage: StorageInfo) {
    val cacheCategory = storage.categories.find { it.icon == CategoryIcon.CACHE }
    val cacheSize = cacheCategory?.sizeBytes ?: 0L

    Card(
        onClick = { if (!isCleaning) onClear() },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCleaning) CleanOrange.copy(alpha = 0.12f) else CleanBlue.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                if (isCleaning) Icons.Default.HourglassTop else Icons.Default.CleaningServices,
                contentDescription = null,
                tint = if (isCleaning) CleanOrange else CleanBlue,
                modifier = Modifier.size(30.dp)
            )
            Column {
                Text(
                    if (isCleaning) "Limpiando…" else "Limpiar Caché",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = if (isCleaning) CleanOrange else CleanBlue
                )
                Text(
                    if (cacheSize > 0) "Liberar hasta ${cacheSize.formatSize()}" else "Elimina archivos temporales de todas las apps",
                    fontSize = 12.sp, color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun CategoryCard(category: StorageCategory, onDeleteItem: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val iconTint = when (category.icon) {
        CategoryIcon.CACHE     -> CleanOrange
        CategoryIcon.APPS      -> CleanBlue
        CategoryIcon.DOWNLOADS -> CleanTeal
        else                   -> TextSecondary
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(categoryIcon(category.icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                    Column {
                        Text(category.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("${category.items.size} elementos", fontSize = 11.sp, color = TextSecondary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(category.sizeBytes.formatSize(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = iconTint)
                    if (category.items.isNotEmpty()) {
                        IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (expanded && category.items.isNotEmpty()) {
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.1f))
                category.items.take(10).forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(item.sizeBytes.formatSize(), fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                    }
                }
                if (category.items.size > 10) {
                    Text("... y ${category.items.size - 10} más", fontSize = 11.sp, color = TextSecondary)
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
