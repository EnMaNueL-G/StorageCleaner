package com.enmanuelgil.storagecleaner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CleanBlue      = Color(0xFF2196F3)
val CleanTeal      = Color(0xFF26C6DA)
val CleanOrange    = Color(0xFFFF7043)
val CleanRed       = Color(0xFFEF5350)
val CleanGreen     = Color(0xFF66BB6A)

val BackgroundDark = Color(0xFF0D1117)
val SurfaceDark    = Color(0xFF161B22)
val CardDark       = Color(0xFF21262D)
val TextPrimary    = Color(0xFFE6EDF3)
val TextSecondary  = Color(0xFF8B949E)

private val DarkColors = darkColorScheme(
    primary      = CleanBlue,
    secondary    = CleanTeal,
    background   = BackgroundDark,
    surface      = SurfaceDark,
    onBackground = TextPrimary,
    onSurface    = TextPrimary
)

@Composable
fun StorageCleanerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}

fun storageColor(usedPct: Float): Color = when {
    usedPct < 0.6f -> CleanGreen
    usedPct < 0.8f -> CleanOrange
    else           -> CleanRed
}
