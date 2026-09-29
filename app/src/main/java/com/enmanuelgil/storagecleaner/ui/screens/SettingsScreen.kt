package com.enmanuelgil.storagecleaner.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.storagecleaner.ui.theme.*

@Composable
fun SettingsScreen() {
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ajustes", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

        // Info de uso
        Text("Consejos que sí funcionan", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TipItem("📥", "Los APK de apps que ya tienes instaladas no sirven para nada: bórralos (pestaña Archivos)")
                TipItem("💬", "WhatsApp y Telegram guardan todo lo recibido: revisa sus vídeos en Archivos grandes y duplicados")
                TipItem("🗑", "Fotos y la Galería guardan lo borrado en su papelera 30-60 días: vacíala desde la propia app")
                TipItem("☁️", "Las fotos ya subidas a la nube se pueden quitar del móvil desde la app de Fotos («Liberar espacio»)")
                TipItem("🧊", "La caché vuelve a crecer al usar las apps; borrarla a diario no ayuda. Úsalo cuando te quedes sin espacio")
            }
        }

        // Donaciones
        Text("Apoya el Proyecto", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, CleanOrange.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = CleanOrange)
                    Text("¿Te fue útil StorageCleaner?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Text("100 % gratis, sin anuncios y de código abierto. Si te ayudó a recuperar espacio, puedes apoyar el desarrollo.",
                    fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.1f))
                DonRow("Binance Pay ID", "1165745950") { clipboard.setText(AnnotatedString("1165745950")) }
                DonRow("BSC BEP20", "0xb6f6731a4ea87f8e1fd6f44f48b5bc4204571f08") {
                    clipboard.setText(AnnotatedString("0xb6f6731a4ea87f8e1fd6f44f48b5bc4204571f08"))
                }
            }
        }

        // Acerca de
        Text("Acerca de", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow2("Versión", "1.1.0")
                InfoRow2("Desarrollado por", "Enmanuel Gil · OptiSuite")
                InfoRow2("Compatibilidad", "Android 8.0+ (API 26)")
                InfoRow2("Root", "No hace falta")
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.1f))
                Text("Sin permiso de Internet: no puede enviar nada fuera del móvil. No recopila datos.", fontSize = 12.sp, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun TipItem(emoji: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(emoji, fontSize = 14.sp)
        Text(text, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
    }
}

@Composable
fun DonRow(label: String, value: String, onCopy: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(value, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CleanOrange)
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun InfoRow2(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Text(value, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}
