package com.enmanuelgil.storagecleaner.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.storagecleaner.ui.theme.*

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
        }
        action?.invoke()
    }
}

@Composable
fun PermissionCard(title: String, text: String, button: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(text, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = CleanBlue)) { Text(button) }
        }
    }
}

@Composable
fun MessageCard(text: String, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CleanBlue.copy(alpha = 0.14f)), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = CleanBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar", tint = TextSecondary, modifier = Modifier.size(18.dp)) }
        }
    }
}

fun openUsageAccess(c: Context) {
    val i = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { data = Uri.parse("package:${c.packageName}") }
    tryStart(c, i, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), appDetails(c))
}

/** Android 11+: pantalla "Acceso a todos los archivos" de esta app. */
fun openAllFilesAccess(c: Context) {
    if (Build.VERSION.SDK_INT < 30) return
    val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${c.packageName}"))
    tryStart(c, i, Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), appDetails(c))
}

fun openAppDetails(c: Context, pkg: String) {
    tryStart(c, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
}

private fun appDetails(c: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))

/** Abre la primera pantalla que exista (algunas capas de fabricante o Android Go no tienen todas). */
private fun tryStart(c: Context, vararg intents: Intent) {
    for (i in intents) { try { c.startActivity(i); return } catch (_: Exception) {} }
}
